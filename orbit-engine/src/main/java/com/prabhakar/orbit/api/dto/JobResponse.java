package com.prabhakar.orbit.api.dto;

import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;

public record JobResponse(
        String jobId,
        String name,
        String type,
        JobStatus status,
        Long nextRunAt,
        Integer attempt,
        Integer maxAttempts,
        Integer timeoutSec,
        String lastError,
        Long createdAt,
        Long updatedAt) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getJobId(),
                job.getJobName(),
                job.getJobType(),
                job.getStatus(),
                job.getNextRunAt(),
                job.getAttempt(),
                job.getMaxAttempts(),
                job.getTimeoutSec(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
