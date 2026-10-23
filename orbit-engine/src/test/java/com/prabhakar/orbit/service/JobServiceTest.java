package com.prabhakar.orbit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prabhakar.orbit.api.dto.SubmitJobRequest;
import com.prabhakar.orbit.config.OrbitProperties;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.domain.ScheduleType;
import com.prabhakar.orbit.repo.JobRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    @Mock
    private JobRepository repository;

    private JobService service;

    @BeforeEach
    void setUp() {
        service = new JobService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OrbitProperties(new OrbitProperties.Poller(1)),
                JsonMapper.builder().build());
    }

    @Test
    void submitSavesAPendingOneOffJobWithDefaults() {
        SubmitJobRequest request = new SubmitJobRequest("report", "SLEEP", Map.of("ms", 200), null, null, null);

        Job returned = service.submit(request);

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(repository).save(saved.capture());
        Job job = saved.getValue();
        assertSame(job, returned);
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(ScheduleType.ONE_OFF, job.getScheduleType());
        assertEquals(NOW.toEpochMilli(), job.getNextRunAt());
        assertEquals("READY#0", job.getReadyShard());
        assertEquals(0, job.getAttempt());
        assertEquals(1L, job.getVersion());
        assertEquals(3, job.getMaxAttempts());
        assertEquals(60, job.getTimeoutSec());
        assertEquals("{\"ms\":200}", job.getPayload());
        assertEquals(NOW.toEpochMilli(), job.getCreatedAt());
    }

    @Test
    void submitHonoursDelayAndOverrides() {
        SubmitJobRequest request = new SubmitJobRequest("later", "FLAKY", null, 5, 120, 30L);

        service.submit(request);

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(repository).save(saved.capture());
        Job job = saved.getValue();
        assertEquals(NOW.toEpochMilli() + 30_000, job.getNextRunAt());
        assertEquals(5, job.getMaxAttempts());
        assertEquals(120, job.getTimeoutSec());
        assertEquals("{}", job.getPayload());
    }

    @Test
    void submitRejectsUnsupportedTypeAndSavesNothing() {
        SubmitJobRequest request = new SubmitJobRequest("bad", "NOPE", null, null, null, null);

        assertThrows(InvalidJobRequestException.class, () -> service.submit(request));

        verify(repository, never()).save(any(Job.class));
    }

    @Test
    void getReturnsTheStoredJob() {
        Job job = new Job();
        job.setJobId("job-1");
        when(repository.findById("job-1")).thenReturn(Optional.of(job));

        assertSame(job, service.get("job-1"));
    }

    @Test
    void getThrowsWhenJobDoesNotExist() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> service.get("missing"));
    }
}
