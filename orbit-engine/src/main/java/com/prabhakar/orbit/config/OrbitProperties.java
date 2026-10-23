package com.prabhakar.orbit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbit")
public record OrbitProperties(Poller poller) {

    public record Poller(int shards) {
    }
}
