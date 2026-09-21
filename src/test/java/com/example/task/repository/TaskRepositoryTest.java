package com.example.task.repository;

import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs the real JPQL against a real (H2) database - this is what proves the claim query is conditional. */
@DataJpaTest
class TaskRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @Autowired
    private TaskRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Task persist(TaskStatus status, Instant nextRunAt, int attempts, int maxAttempts, Instant startedAt) {
        Task task = new Task();
        task.setTitle("t");
        task.setStatus(status);
        task.setNextRunAt(nextRunAt);
        task.setAttempts(attempts);
        task.setMaxAttempts(maxAttempts);
        task.setStartedAt(startedAt);
        task.setCreatedAt(NOW);
        return entityManager.persistAndFlush(task);
    }

    @Test
    void claimSucceedsOnceAndThenLoses() {
        Task task = persist(TaskStatus.PENDING, NOW, 0, 3, null);

        int first = repository.claim(task.getId(), TaskStatus.PENDING, TaskStatus.RUNNING, NOW);
        int second = repository.claim(task.getId(), TaskStatus.PENDING, TaskStatus.RUNNING, NOW);

        assertEquals(1, first);
        assertEquals(0, second);                                         // status is no longer PENDING -> no update
        Task reloaded = repository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.RUNNING, reloaded.getStatus());
        assertEquals(1, reloaded.getAttempts());                         // incremented exactly once
        assertEquals(NOW, reloaded.getStartedAt());
    }

    @Test
    void claimDoesNotTouchTasksInOtherStates() {
        Task finished = persist(TaskStatus.SUCCEEDED, null, 1, 3, NOW);

        assertEquals(0, repository.claim(finished.getId(), TaskStatus.PENDING, TaskStatus.RUNNING, NOW));
        assertEquals(TaskStatus.SUCCEEDED, repository.findById(finished.getId()).orElseThrow().getStatus());
    }

    @Test
    void findDueIdsReturnsOnlyPendingTasksThatAreDueOldestFirst() {
        Task older = persist(TaskStatus.PENDING, NOW.minusSeconds(60), 0, 3, null);
        Task newer = persist(TaskStatus.PENDING, NOW.minusSeconds(5), 0, 3, null);
        persist(TaskStatus.PENDING, NOW.plusSeconds(60), 0, 3, null);   // backoff not over yet
        persist(TaskStatus.RUNNING, NOW.minusSeconds(60), 1, 3, NOW);   // already running

        List<Long> due = repository.findDueIds(TaskStatus.PENDING, NOW, PageRequest.of(0, 10));

        assertEquals(List.of(older.getId(), newer.getId()), due);
    }

    @Test
    void findDueIdsRespectsTheBatchSize() {
        persist(TaskStatus.PENDING, NOW.minusSeconds(3), 0, 3, null);
        persist(TaskStatus.PENDING, NOW.minusSeconds(2), 0, 3, null);
        persist(TaskStatus.PENDING, NOW.minusSeconds(1), 0, 3, null);

        assertEquals(2, repository.findDueIds(TaskStatus.PENDING, NOW, PageRequest.of(0, 2)).size());
    }

    @Test
    void staleRunningTasksAreRequeuedOrFailedDependingOnRemainingAttempts() {
        Instant cutoff = NOW.minusSeconds(300);
        Instant longAgo = NOW.minusSeconds(3600);
        Task withAttemptsLeft = persist(TaskStatus.RUNNING, null, 1, 3, longAgo);
        Task exhausted = persist(TaskStatus.RUNNING, null, 3, 3, longAgo);
        Task stillFresh = persist(TaskStatus.RUNNING, null, 1, 3, NOW.minusSeconds(10));

        int requeued = repository.requeueStale(TaskStatus.RUNNING, TaskStatus.PENDING, cutoff, NOW, "recovered");
        int failed = repository.failStale(TaskStatus.RUNNING, TaskStatus.FAILED, cutoff, NOW, "recovered");

        assertEquals(1, requeued);
        assertEquals(1, failed);
        Task a = repository.findById(withAttemptsLeft.getId()).orElseThrow();
        assertEquals(TaskStatus.PENDING, a.getStatus());
        assertEquals(NOW, a.getNextRunAt());
        Task b = repository.findById(exhausted.getId()).orElseThrow();
        assertEquals(TaskStatus.FAILED, b.getStatus());
        assertNull(b.getNextRunAt());
        assertEquals(TaskStatus.RUNNING, repository.findById(stillFresh.getId()).orElseThrow().getStatus());
    }

    @Test
    void findByStatusFiltersAndCountByStatusCounts() {
        persist(TaskStatus.PENDING, NOW, 0, 3, null);
        persist(TaskStatus.FAILED, null, 3, 3, NOW);

        assertEquals(1, repository.findByStatus(TaskStatus.FAILED, PageRequest.of(0, 10)).size());
        assertEquals(1L, repository.countByStatus(TaskStatus.PENDING));
    }
}
