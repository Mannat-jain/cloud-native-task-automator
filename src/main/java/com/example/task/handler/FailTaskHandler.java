package com.example.task.handler;

import com.example.task.model.Task;
import com.example.task.model.TaskType;
import org.springframework.stereotype.Component;

/** Always fails. Useful to demonstrate and test retries, exponential backoff and the FAILED state. */
@Component
public class FailTaskHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.FAIL;
    }

    @Override
    public String handle(Task task) {
        throw new IllegalStateException("Simulated failure for task " + task.getId());
    }
}
