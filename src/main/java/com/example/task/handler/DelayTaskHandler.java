package com.example.task.handler;

import com.example.task.model.Task;
import com.example.task.model.TaskType;
import org.springframework.stereotype.Component;

/** Simulates slow work: sleeps for {@code payload} milliseconds (default 100, capped at 5 s). */
@Component
public class DelayTaskHandler implements TaskHandler {

    static final long DEFAULT_DELAY_MS = 100;
    static final long MAX_DELAY_MS = 5_000;

    @Override
    public TaskType type() {
        return TaskType.DELAY;
    }

    @Override
    public String handle(Task task) throws InterruptedException {
        long millis = parse(task.getPayload());
        Thread.sleep(millis);
        return "Waited " + millis + " ms";
    }

    static long parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return DEFAULT_DELAY_MS;
        }
        try {
            long value = Long.parseLong(payload.trim());
            if (value < 0) {
                throw new IllegalArgumentException("DELAY payload must not be negative");
            }
            return Math.min(value, MAX_DELAY_MS);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("DELAY payload must be a number of milliseconds, got: " + payload);
        }
    }
}
