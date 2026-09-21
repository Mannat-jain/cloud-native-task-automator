package com.example.task.dto;

import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.model.TaskType;

import java.time.Instant;

/** Response representation of a task. */
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskType type,
        String payload,
        TaskStatus status,
        int attempts,
        int maxAttempts,
        Instant createdAt,
        Instant nextRunAt,
        Instant startedAt,
        Instant finishedAt,
        String result,
        String lastError
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(), t.getTitle(), t.getDescription(), t.getType(), t.getPayload(), t.getStatus(),
                t.getAttempts(), t.getMaxAttempts(), t.getCreatedAt(), t.getNextRunAt(), t.getStartedAt(),
                t.getFinishedAt(), t.getResult(), t.getLastError());
    }
}
