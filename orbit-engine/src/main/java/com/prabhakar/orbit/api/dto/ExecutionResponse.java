package com.prabhakar.orbit.api.dto;

import com.prabhakar.orbit.domain.Execution;
import com.prabhakar.orbit.domain.ExecutionStatus;

public record ExecutionResponse(
        Integer attempt,
        ExecutionStatus status,
        String workerId,
        Long startedAt,
        Long finishedAt,
        Long durationMs,
        String errorMessage) {

    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getAttempt(),
                execution.getStatus(),
                execution.getWorkerId(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getDurationMs(),
                execution.getErrorMessage());
    }
}
