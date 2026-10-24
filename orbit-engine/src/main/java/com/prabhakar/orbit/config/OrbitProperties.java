package com.prabhakar.orbit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbit")
public record OrbitProperties(Poller poller, Workers workers, Lease lease) {

    public record Poller(int shards, long intervalMs) {
    }

    public record Workers(int size, int queue) {
    }

    public record Lease(long ms) {
    }
}
