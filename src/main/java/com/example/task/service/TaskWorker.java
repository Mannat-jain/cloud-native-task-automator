package com.example.task.service;

import com.example.task.model.TaskStatus;
import com.example.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Background poller: every few seconds it finds due PENDING tasks, CLAIMS each one (atomic conditional UPDATE)
 * and hands it to a bounded thread pool. Safe to run on several application instances at once - only the
 * instance that wins the claim executes the task. (At larger scale: {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * or a real queue.)
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final TaskRepository repository;
    private final TaskLifecycle lifecycle;
    private final TaskExecutionService execution;
    private final Executor executor;
    private final Clock clock;
    private final int batchSize;
    private final Duration staleAfter;

    public TaskWorker(TaskRepository repository,
                      TaskLifecycle lifecycle,
                      TaskExecutionService execution,
                      @Qualifier("taskWorkerExecutor") Executor executor,
                      Clock clock,
                      @Value("${app.worker.batch-size:10}") int batchSize,
                      @Value("${app.worker.stale-after-seconds:300}") long staleAfterSeconds) {
        this.repository = repository;
        this.lifecycle = lifecycle;
        this.execution = execution;
        this.executor = executor;
        this.clock = clock;
        this.batchSize = batchSize;
        this.staleAfter = Duration.ofSeconds(staleAfterSeconds);
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-interval-ms:2000}",
            initialDelayString = "${app.worker.initial-delay-ms:5000}")
    public void poll() {
        try {
            int recovered = lifecycle.recoverStale(staleAfter);
            if (recovered > 0) {
                log.warn("Recovered {} stale RUNNING task(s)", recovered);
            }
            List<Long> dueIds = repository.findDueIds(TaskStatus.PENDING, clock.instant(), PageRequest.of(0, batchSize));
            for (Long id : dueIds) {
                if (lifecycle.claim(id)) {                             // only the winner of the claim runs it
                    executor.execute(() -> runSafely(id));
                }
            }
        } catch (RuntimeException e) {
            log.error("Worker poll failed - will retry on the next tick", e);   // never let the scheduler thread die
        }
    }

    private void runSafely(Long id) {
        try {
            execution.runClaimed(id);
        } catch (RuntimeException e) {
            log.error("Unexpected error while running task {}", id, e);
        }
    }
}
