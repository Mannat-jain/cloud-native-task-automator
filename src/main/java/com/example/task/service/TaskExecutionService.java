package com.example.task.service;

import com.example.task.exception.InvalidTaskStateException;
import com.example.task.exception.TaskNotFoundException;
import com.example.task.handler.TaskHandler;
import com.example.task.model.Task;
import com.example.task.model.TaskType;
import com.example.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs tasks. Deliberately NOT transactional: the handler runs outside any transaction and
 * {@link TaskLifecycle} records the outcome in its own short transactions.
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskRepository repository;
    private final TaskLifecycle lifecycle;
    private final Map<TaskType, TaskHandler> handlers = new EnumMap<>(TaskType.class);

    public TaskExecutionService(TaskRepository repository, TaskLifecycle lifecycle, List<TaskHandler> handlerBeans) {
        this.repository = repository;
        this.lifecycle = lifecycle;
        for (TaskHandler handler : handlerBeans) {
            this.handlers.put(handler.type(), handler);
        }
        for (TaskType type : TaskType.values()) {                       // fail fast at startup, not at 3 a.m.
            if (!handlers.containsKey(type)) {
                throw new IllegalStateException("No TaskHandler registered for task type " + type);
            }
        }
    }

    /** "Run it now" from the API. Claims the task first, so it can never run twice concurrently. */
    public Task executeNow(Long id) {
        Task task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!lifecycle.claim(id)) {
            throw new InvalidTaskStateException(
                    "Task " + id + " cannot be executed: only PENDING tasks can run (current status: "
                            + task.getStatus() + ")");
        }
        return runClaimed(id);
    }

    /** Runs a task that the caller has ALREADY claimed (status RUNNING). Never throws for handler failures. */
    public Task runClaimed(Long id) {
        Task task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        TaskHandler handler = handlers.get(task.getType());
        try {
            String result = handler.handle(task);
            return lifecycle.markSucceeded(id, result);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();                     // keep the interrupt flag
            }
            log.warn("Task {} (attempt {}) failed: {}", id, task.getAttempts(), e.toString());
            return lifecycle.markAttemptFailed(id, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
