package com.prabhakar.orbit.domain;

import java.util.EnumSet;
import java.util.Set;

public enum JobStatus {
    PENDING,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public Set<JobStatus> allowedNext() {
        return switch (this) {
            case PENDING, RETRYING -> EnumSet.of(RUNNING, CANCELLED);
            case RUNNING -> EnumSet.of(SUCCEEDED, FAILED, RETRYING, PENDING);
            case SUCCEEDED, FAILED, CANCELLED -> EnumSet.noneOf(JobStatus.class);
        };
    }

    public boolean canMoveTo(JobStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}