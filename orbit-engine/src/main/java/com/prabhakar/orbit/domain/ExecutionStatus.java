package com.prabhakar.orbit.domain;

public enum ExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    // The result could not be saved because this worker no longer owned the job.
    LEASE_LOST
}
