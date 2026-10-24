package com.prabhakar.orbit.engine;

import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.handler.JobContext;
import com.prabhakar.orbit.handler.JobHandler;
import com.prabhakar.orbit.handler.JobHandlerRegistry;
import com.prabhakar.orbit.repo.JobRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JobHandlerRegistry registry;
    private final JobRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public JobRunner(JobHandlerRegistry registry, JobRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Runs a job this worker has already claimed. Until retries exist, any failure ends the job as FAILED. */
    public void run(Job job) {
        log.info("Job started jobId={} type={} attempt={}", job.getJobId(), job.getJobType(), job.getAttempt());
        try {
            JobHandler handler = registry.find(job.getJobType())
                    .orElseThrow(() -> new IllegalStateException("No handler for job type " + job.getJobType()));
            handler.handle(new JobContext(job.getJobId(), job.getAttempt(), readPayload(job)));
            finish(job, JobStatus.SUCCEEDED, null);
        } catch (InterruptedException e) {
            finish(job, JobStatus.FAILED, "Interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Job failed jobId={}: {}", job.getJobId(), e.toString());
            finish(job, JobStatus.FAILED, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> readPayload(Job job) {
        String payload = job.getPayload();
        return payload == null || payload.isBlank() ? Map.of() : jsonMapper.readValue(payload, PAYLOAD_TYPE);
    }

    private void finish(Job job, JobStatus outcome, String error) {
        try {
            if (repository.finish(job, outcome, clock.millis(), error)) {
                log.info("Job finished jobId={} status={}", job.getJobId(), outcome);
            } else {
                log.warn("Lease lost, result discarded jobId={}", job.getJobId());
            }
        } catch (RuntimeException e) {
            log.error("Could not save result jobId={}; the lease will expire and the job will be recovered",
                    job.getJobId(), e);
        }
    }
}
