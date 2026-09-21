package com.example.task.model;

/** Built-in task kinds. Each one is executed by a {@code TaskHandler} (Strategy pattern). */
public enum TaskType {
    /** Returns the task description as its result. */
    ECHO,
    /** Sleeps for {@code payload} milliseconds (capped), simulating slow work. */
    DELAY,
    /** Always fails - used to exercise retries, backoff and the FAILED state. */
    FAIL
}
