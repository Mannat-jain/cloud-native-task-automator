package com.example.task.dto;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned for every failed request. */
public record ApiError(int status, String code, String message, Map<String, String> details, Instant timestamp) {
}
