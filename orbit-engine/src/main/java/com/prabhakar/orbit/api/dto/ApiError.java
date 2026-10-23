package com.prabhakar.orbit.api.dto;

import java.util.List;

public record ApiError(int status, String message, List<String> details) {
}
