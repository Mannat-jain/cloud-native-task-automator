package com.example.task.dto;

import com.example.task.model.TaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/tasks}. Entities are never exposed directly.
 * {@code type} defaults to ECHO and {@code maxAttempts} to 3 when omitted.
 */
public record CreateTaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        TaskType type,

        @Size(max = 1000, message = "payload must be at most 1000 characters")
        String payload,

        @Min(value = 1, message = "maxAttempts must be at least 1")
        @Max(value = 10, message = "maxAttempts must be at most 10")
        Integer maxAttempts
) {
}
