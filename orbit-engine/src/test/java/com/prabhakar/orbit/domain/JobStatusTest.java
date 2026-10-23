package com.prabhakar.orbit.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class JobStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "PENDING, RUNNING",
            "PENDING, CANCELLED",
            "RETRYING, RUNNING",
            "RETRYING, CANCELLED",
            "RUNNING, SUCCEEDED",
            "RUNNING, FAILED",
            "RUNNING, RETRYING",
            "RUNNING, PENDING"
    })
    void shouldAllowLegalTransitions(JobStatus from, JobStatus to) {
        assertTrue(from.canMoveTo(to));
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "PENDING, SUCCEEDED",
            "PENDING, FAILED",
            "PENDING, PENDING",
            "RETRYING, PENDING",
            "RETRYING, SUCCEEDED",
            "RUNNING, RUNNING",
            "RUNNING, CANCELLED",
            "SUCCEEDED, RUNNING",
            "SUCCEEDED, PENDING",
            "FAILED, RETRYING",
            "FAILED, RUNNING",
            "CANCELLED, PENDING",
            "CANCELLED, RUNNING"
    })
    void shouldRejectIllegalTransitions(JobStatus from, JobStatus to) {
        assertFalse(from.canMoveTo(to));
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    void terminalStatesAreTerminalAndMoveNowhere(JobStatus terminal) {
        assertTrue(terminal.isTerminal());
        for (JobStatus next : JobStatus.values()) {
            assertFalse(terminal.canMoveTo(next));
        }
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "PENDING", "RUNNING", "RETRYING" })
    void activeStatesAreNotTerminal(JobStatus active) {
        assertFalse(active.isTerminal());
    }
}