package com.prabhakar.orbit.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prabhakar.orbit.domain.Execution;
import com.prabhakar.orbit.domain.ExecutionStatus;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.handler.JobContext;
import com.prabhakar.orbit.handler.JobHandler;
import com.prabhakar.orbit.handler.JobHandlerRegistry;
import com.prabhakar.orbit.repo.ExecutionRepository;
import com.prabhakar.orbit.repo.JobRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final long NOW_MS = NOW.toEpochMilli();

    @Mock
    private JobRepository repository;

    @Mock
    private JobHandler handler;

    @Mock
    private ExecutionRepository executionRepository;

    private JobRunner runner;

    @BeforeEach
    void setUp() {
        when(handler.type()).thenReturn("SLEEP");
        runner = new JobRunner(
                new JobHandlerRegistry(List.of(handler)),
                repository,
                executionRepository,
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksTheJobSucceededWhenTheHandlerReturns() throws Exception {
        Job job = job("SLEEP", "{\"ms\":200}");

        runner.run(job);

        verify(handler).handle(any(JobContext.class));
        verify(repository).finish(eq(job), eq(JobStatus.SUCCEEDED), eq(NOW_MS), isNull());
    }

    @Test
    void passesTheParsedPayloadToTheHandler() throws Exception {
        Job job = job("SLEEP", "{\"ms\":200}");

        runner.run(job);

        ArgumentCaptor<JobContext> context = ArgumentCaptor.forClass(JobContext.class);
        verify(handler).handle(context.capture());
        assertEquals("job-1", context.getValue().jobId());
        assertEquals(1, context.getValue().attempt());
        assertEquals(200L, context.getValue().longValue("ms", 0));
    }

    @Test
    void marksTheJobFailedWithTheMessageWhenTheHandlerThrows() throws Exception {
        Job job = job("SLEEP", "{}");
        doThrow(new IllegalStateException("boom")).when(handler).handle(any(JobContext.class));

        runner.run(job);

        verify(repository).finish(job, JobStatus.FAILED, NOW_MS, "boom");
    }

    @Test
    void failsTheJobWhenNoHandlerExistsForItsType() throws Exception {
        Job job = job("NOPE", "{}");

        runner.run(job);

        verify(handler, never()).handle(any(JobContext.class));
        verify(repository).finish(job, JobStatus.FAILED, NOW_MS, "No handler for job type NOPE");
    }

    @Test
    void aLostLeaseDoesNotThrow() {
        Job job = job("SLEEP", "{}");
        when(repository.finish(any(Job.class), any(JobStatus.class), anyLong(), any())).thenReturn(false);

        assertDoesNotThrow(() -> runner.run(job));
    }

    @Test
    void aDatabaseErrorWhileSavingTheResultDoesNotEscape() {
        Job job = job("SLEEP", "{}");
        when(repository.finish(any(Job.class), any(JobStatus.class), anyLong(), any()))
                .thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> runner.run(job));
    }

    @Test
    void recordsASucceededRunInTheHistory() {
        Job job = job("SLEEP", "{}");
        when(repository.finish(eq(job), eq(JobStatus.SUCCEEDED), eq(NOW_MS), isNull())).thenReturn(true);

        runner.run(job);

        Execution execution = savedExecution();
        assertEquals("job-1", execution.getJobId());
        assertEquals(Execution.sortKey(NOW_MS, 1), execution.getSk());
        assertEquals(1, execution.getAttempt());
        assertEquals("worker-A", execution.getWorkerId());
        assertEquals(ExecutionStatus.SUCCEEDED, execution.getStatus());
        assertEquals(NOW_MS, execution.getStartedAt());
        assertEquals(NOW_MS, execution.getFinishedAt());
        assertEquals(0L, execution.getDurationMs());
    }

    @Test
    void recordsAFailedRunWithItsErrorMessage() throws Exception {
        Job job = job("SLEEP", "{}");
        doThrow(new IllegalStateException("boom")).when(handler).handle(any(JobContext.class));
        when(repository.finish(job, JobStatus.FAILED, NOW_MS, "boom")).thenReturn(true);

        runner.run(job);

        Execution execution = savedExecution();
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertEquals("boom", execution.getErrorMessage());
    }

    @Test
    void recordsLeaseLostWhenTheResultCouldNotBeSaved() {
        Job job = job("SLEEP", "{}");
        when(repository.finish(any(Job.class), any(JobStatus.class), anyLong(), any())).thenReturn(false);

        runner.run(job);

        assertEquals(ExecutionStatus.LEASE_LOST, savedExecution().getStatus());
    }

    @Test
    void aHistoryWriteFailureDoesNotStopTheJob() {
        Job job = job("SLEEP", "{}");
        doThrow(new IllegalStateException("database down")).when(executionRepository).save(any(Execution.class));

        assertDoesNotThrow(() -> runner.run(job));

        verify(repository).finish(eq(job), eq(JobStatus.SUCCEEDED), eq(NOW_MS), isNull());
    }

    // The same object is saved twice (RUNNING, then the final result), so the captor holds its final state.
    private Execution savedExecution() {
        ArgumentCaptor<Execution> saved = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository, times(2)).save(saved.capture());
        return saved.getValue();
    }

    private static Job job(String type, String payload) {
        Job job = new Job();
        job.setJobId("job-1");
        job.setJobType(type);
        job.setPayload(payload);
        job.setAttempt(1);
        job.setLeaseOwner("worker-A");
        return job;
    }
}
