package com.prabhakar.orbit.api.dto;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitJobRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String type,
        Map<String, Object> payload,
        @Min(1) @Max(10) Integer maxAttempts,
        @Min(1) @Max(900) Integer timeoutSec,
        @Min(0) Long delaySeconds) {
}
