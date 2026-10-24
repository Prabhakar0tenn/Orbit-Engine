package com.prabhakar.orbit.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class JobHandlerRegistryTest {

    @Test
    void findsHandlersByTheirType() {
        SleepHandler sleep = new SleepHandler();
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(sleep));

        assertTrue(registry.supports("SLEEP"));
        assertSame(sleep, registry.find("SLEEP").orElseThrow());
    }

    @Test
    void reportsUnknownTypesAsUnsupported() {
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(new SleepHandler()));

        assertFalse(registry.supports("NOPE"));
        assertTrue(registry.find("NOPE").isEmpty());
    }

    @Test
    void refusesTwoHandlersForTheSameType() {
        List<JobHandler> duplicates = List.of(new SleepHandler(), new SleepHandler());

        assertThrows(IllegalStateException.class, () -> new JobHandlerRegistry(duplicates));
    }
}
