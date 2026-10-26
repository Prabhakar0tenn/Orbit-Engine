package com.prabhakar.orbit.engine;

import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prabhakar.orbit.domain.Execution;
import com.prabhakar.orbit.domain.ExecutionStatus;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.handler.JobContext;
import com.prabhakar.orbit.handler.JobHandler;
import com.prabhakar.orbit.handler.JobHandlerRegistry;
import com.prabhakar.orbit.repo.ExecutionRepository;
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
    private final ExecutionRepository executionRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public JobRunner(JobHandlerRegistry registry, JobRepository repository,
            ExecutionRepository executionRepository, JsonMapper jsonMapper, Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Runs a job this worker has already claimed. Until retries exist, any failure ends the job as FAILED. */
    public void run(Job job) {
        log.info("Job started jobId={} type={} attempt={}", job.getJobId(), job.getJobType(), job.getAttempt());
        Execution execution = startExecution(job);

        JobStatus outcome = JobStatus.SUCCEEDED;
        String error = null;
        boolean interrupted = false;
        try {
            JobHandler handler = registry.find(job.getJobType())
                    .orElseThrow(() -> new IllegalStateException("No handler for job type " + job.getJobType()));
            handler.handle(new JobContext(job.getJobId(), job.getAttempt(), readPayload(job)));
        } catch (InterruptedException e) {
            outcome = JobStatus.FAILED;
            error = "Interrupted";
            interrupted = true;
        } catch (Exception e) {
            log.warn("Job failed jobId={}: {}", job.getJobId(), e.toString());
            outcome = JobStatus.FAILED;
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        boolean saved = saveOutcome(job, outcome, error);
        completeExecution(execution, saved ? ExecutionStatus.valueOf(outcome.name()) : ExecutionStatus.LEASE_LOST, error);
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> readPayload(Job job) {
        String payload = job.getPayload();
        return payload == null || payload.isBlank() ? Map.of() : jsonMapper.readValue(payload, PAYLOAD_TYPE);
    }

    private boolean saveOutcome(Job job, JobStatus outcome, String error) {
        try {
            if (repository.finish(job, outcome, clock.millis(), error)) {
                log.info("Job finished jobId={} status={}", job.getJobId(), outcome);
                return true;
            }
            log.warn("Lease lost, result discarded jobId={}", job.getJobId());
        } catch (RuntimeException e) {
            log.error("Could not save result jobId={}; the lease will expire and the job will be recovered",
                    job.getJobId(), e);
        }
        return false;
    }

    private Execution startExecution(Job job) {
        long startedAt = clock.millis();
        Execution execution = new Execution();
        execution.setJobId(job.getJobId());
        execution.setSk(Execution.sortKey(startedAt, job.getAttempt()));
        execution.setAttempt(job.getAttempt());
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setWorkerId(job.getLeaseOwner());
        execution.setStartedAt(startedAt);
        saveExecution(execution);
        return execution;
    }

    private void completeExecution(Execution execution, ExecutionStatus status, String error) {
        long finishedAt = clock.millis();
        execution.setStatus(status);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(finishedAt - execution.getStartedAt());
        execution.setErrorMessage(error);
        saveExecution(execution);
    }

    // History is for humans: a failure to record it must never stop the job itself.
    private void saveExecution(Execution execution) {
        try {
            executionRepository.save(execution);
        } catch (RuntimeException e) {
            log.warn("Could not save execution history jobId={}", execution.getJobId(), e);
        }
    }
}
