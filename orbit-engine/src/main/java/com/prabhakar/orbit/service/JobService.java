package com.prabhakar.orbit.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prabhakar.orbit.api.dto.SubmitJobRequest;
import com.prabhakar.orbit.config.OrbitProperties;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.domain.ReadyShard;
import com.prabhakar.orbit.domain.ScheduleType;
import com.prabhakar.orbit.handler.JobHandlerRegistry;
import com.prabhakar.orbit.repo.JobRepository;

import tools.jackson.databind.json.JsonMapper;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_TIMEOUT_SEC = 60;
    private static final long DEFAULT_BACKOFF_BASE_MS = 1_000;
    private static final long DEFAULT_BACKOFF_CAP_MS = 60_000;

    private final JobRepository repository;
    private final JobHandlerRegistry handlerRegistry;
    private final Clock clock;
    private final OrbitProperties properties;
    private final JsonMapper jsonMapper;

    public JobService(JobRepository repository, JobHandlerRegistry handlerRegistry, Clock clock,
            OrbitProperties properties, JsonMapper jsonMapper) {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
        this.clock = clock;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public Job submit(SubmitJobRequest request) {
        if (!handlerRegistry.supports(request.type())) {
            throw new InvalidJobRequestException("Unsupported job type: " + request.type());
        }

        long now = clock.millis();
        long delayMs = request.delaySeconds() == null ? 0 : request.delaySeconds() * 1_000;
        String jobId = UUID.randomUUID().toString();

        Job job = new Job();
        job.setJobId(jobId);
        job.setJobName(request.name());
        job.setJobType(request.type());
        job.setPayload(request.payload() == null ? "{}" : jsonMapper.writeValueAsString(request.payload()));
        job.setScheduleType(ScheduleType.ONE_OFF);
        job.setStatus(JobStatus.PENDING);
        job.setNextRunAt(now + delayMs);
        job.setAttempt(0);
        job.setMaxAttempts(request.maxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : request.maxAttempts());
        job.setTimeoutSec(request.timeoutSec() == null ? DEFAULT_TIMEOUT_SEC : request.timeoutSec());
        job.setBackoffBaseMs(DEFAULT_BACKOFF_BASE_MS);
        job.setBackoffCapMs(DEFAULT_BACKOFF_CAP_MS);
        job.setCancelRequested(false);
        job.setVersion(1L);
        job.setReadyShard(ReadyShard.name(Math.floorMod(jobId.hashCode(), properties.poller().shards())));
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        repository.save(job);
        log.info("Submitted job jobId={} type={} nextRunAt={}", jobId, job.getJobType(), job.getNextRunAt());
        return job;
    }

    public Job get(String jobId) {
        return repository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
    }
}
