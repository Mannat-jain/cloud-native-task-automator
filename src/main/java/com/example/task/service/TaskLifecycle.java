package com.example.task.service;

import com.example.task.exception.TaskNotFoundException;
import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Every task state transition, each in its own short transaction.
 *
 * <p>These are deliberately separate from running the handler: the handler executes OUTSIDE any transaction,
 * so a slow task never holds a database connection (which would starve the HikariCP pool). It is also a
 * separate bean, because {@code @Transactional} works through a proxy and would be skipped on a
 * self-invocation from {@link TaskExecutionService}.
 */
@Service
public class TaskLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TaskLifecycle.class);

    private final TaskRepository repository;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public TaskLifecycle(TaskRepository repository, RetryPolicy retryPolicy, Clock clock) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    /** @return true if THIS caller won the right to run the task (PENDING -> RUNNING), false otherwise. */
    @Transactional
    public boolean claim(Long id) {
        return repository.claim(id, TaskStatus.PENDING, TaskStatus.RUNNING, clock.instant()) == 1;
    }

    @Transactional
    public Task markSucceeded(Long id, String result) {
        Task task = load(id);
        if (task.getStatus() != TaskStatus.RUNNING) {
            log.warn("Task {} finished but is {} (it was requeued or failed meanwhile) - ignoring result",
                    id, task.getStatus());
            return task;
        }
        Instant now = clock.instant();
        task.setStatus(TaskStatus.SUCCEEDED);
        task.setResult(truncate(result, 2000));
        task.setLastError(null);
        task.setNextRunAt(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        return task;                       // managed entity: dirty checking flushes the UPDATE on commit
    }

    /** Retry with exponential backoff while attempts remain, otherwise FAILED. */
    @Transactional
    public Task markAttemptFailed(Long id, String error) {
        Task task = load(id);
        if (task.getStatus() != TaskStatus.RUNNING) {
            log.warn("Task {} failed but is {} (it was requeued or failed meanwhile) - ignoring", id, task.getStatus());
            return task;
        }
        Instant now = clock.instant();
        task.setLastError(truncate(error, 1000));
        task.setUpdatedAt(now);
        if (task.getAttempts() >= task.getMaxAttempts()) {
            task.setStatus(TaskStatus.FAILED);
            task.setNextRunAt(null);
            task.setFinishedAt(now);
            log.warn("Task {} FAILED permanently after {} attempt(s): {}", id, task.getAttempts(), error);
        } else {
            Duration delay = retryPolicy.delayAfterAttempt(task.getAttempts());
            task.setStatus(TaskStatus.PENDING);
            task.setNextRunAt(now.plus(delay));
            log.info("Task {} attempt {}/{} failed, retrying in {} ms",
                    id, task.getAttempts(), task.getMaxAttempts(), delay.toMillis());
        }
        return task;
    }

    /** Recover tasks a dead worker left RUNNING: requeue if attempts remain, else FAIL. @return how many were touched */
    @Transactional
    public int recoverStale(Duration staleAfter) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(staleAfter);
        String reason = "Recovered after worker timeout (no result within " + staleAfter.toSeconds() + "s)";
        int requeued = repository.requeueStale(TaskStatus.RUNNING, TaskStatus.PENDING, cutoff, now, reason);
        int failed = repository.failStale(TaskStatus.RUNNING, TaskStatus.FAILED, cutoff, now, reason);
        return requeued + failed;
    }

    private Task load(Long id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
