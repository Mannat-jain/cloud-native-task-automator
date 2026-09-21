package com.example.task.handler;

import com.example.task.model.Task;
import com.example.task.model.TaskType;

/**
 * Strategy for executing one kind of task. Add a new task type by adding an enum constant and one
 * {@code @Component} implementing this interface - nothing else changes (Open/Closed principle).
 *
 * <p>Execution is <b>at-least-once</b> (a crashed worker's task is retried), so handlers should be idempotent.
 */
public interface TaskHandler {

    TaskType type();

    /** @return a short human-readable result; throw any exception to signal failure (the attempt is retried). */
    String handle(Task task) throws Exception;
}
