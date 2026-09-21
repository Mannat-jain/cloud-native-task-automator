package com.example.task.handler;

import com.example.task.model.Task;
import com.example.task.model.TaskType;
import org.springframework.stereotype.Component;

@Component
public class EchoTaskHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.ECHO;
    }

    @Override
    public String handle(Task task) {
        return task.getDescription() != null ? task.getDescription() : task.getTitle();
    }
}
