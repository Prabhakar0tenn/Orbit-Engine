package com.prabhakar.orbit.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prabhakar.orbit.config.OrbitProperties;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.repo.JobRepository;

@ExtendWith(MockitoExtension.class)
class JobPollerTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final long NOW_MS = NOW.toEpochMilli();

    @Mock
    private JobRepository repository;

    @Mock
    private WorkerPool workerPool;

    @Mock
    private JobRunner jobRunner;

    private JobPoller poller;

    @BeforeEach
    void setUp() {
        poller = new JobPoller(
                repository,
                workerPool,
                jobRunner,
                new WorkerIdentity(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OrbitProperties(
                        new OrbitProperties.Poller(1, 1000),
                        new OrbitProperties.Workers(4, 10),
                        new OrbitProperties.Lease(30_000)));
    }

    @Test
    void doesNothingWhenNoWorkerSlotIsFree() {
        when(workerPool.freeSlots()).thenReturn(0);

        poller.pollOnce();

        verifyNoInteractions(repository, jobRunner);
    }

    @Test
    void handsOnlyTheClaimedJobsToTheWorkerPool() {
        Job won = job("job-won");
        Job lost = job("job-lost");
        when(workerPool.freeSlots()).thenReturn(4);
        when(repository.findReady("READY#0", NOW_MS, 4)).thenReturn(List.of(won, lost));
        when(repository.claim(eq(won), anyString(), eq(NOW_MS), eq(30_000L))).thenReturn(Optional.of(won));
        when(repository.claim(eq(lost), anyString(), eq(NOW_MS), eq(30_000L))).thenReturn(Optional.empty());

        poller.pollOnce();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(workerPool, times(1)).execute(task.capture());
        task.getValue().run();
        verify(jobRunner).run(won);
    }

    @Test
    void asksForNoMoreJobsThanThereAreFreeSlots() {
        when(workerPool.freeSlots()).thenReturn(2);
        when(repository.findReady(anyString(), anyLong(), eq(2))).thenReturn(List.of());

        poller.pollOnce();

        verify(repository).findReady("READY#0", NOW_MS, 2);
    }

    @Test
    void aFailedPollDoesNotStopTheScheduler() {
        when(workerPool.freeSlots()).thenReturn(4);
        when(repository.findReady(anyString(), anyLong(), any(Integer.class)))
                .thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(poller::poll);
    }

    private static Job job(String jobId) {
        Job job = new Job();
        job.setJobId(jobId);
        return job;
    }
}
