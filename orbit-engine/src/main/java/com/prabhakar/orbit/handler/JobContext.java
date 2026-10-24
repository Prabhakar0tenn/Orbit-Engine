package com.prabhakar.orbit.handler;

import java.util.Map;

public record JobContext(String jobId, int attempt, Map<String, Object> payload) {

    public long longValue(String key, long fallback) {
        return payload.get(key) instanceof Number number ? number.longValue() : fallback;
    }
}
