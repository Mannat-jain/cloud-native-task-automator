package com.example.task.service;

import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TaskLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private TaskRepository repository;
    private TaskLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TaskRepository.class);
        RetryPolicy retryPolicy = new RetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(5));
        lifecycle = new TaskLifecycle(repository, retryPolicy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Task runningTask(int attempts, int maxAttempts) {
        Task task = new Task();
        task.setId(1L);
        task.setTitle("t");
        task.setStatus(TaskStatus.RUNNING);
        task.setAttempts(attempts);
        task.setMaxAttempts(maxAttempts);
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    void claimReturnsTrueOnlyWhenExactlyOneRowWasUpdated() {
        when(repository.claim(1L, TaskStatus.PENDING, TaskStatus.RUNNING, NOW)).thenReturn(1);
        when(repository.claim(2L, TaskStatus.PENDING, TaskStatus.RUNNING, NOW)).thenReturn(0);

        assertTrue(lifecycle.claim(1L));
        assertFalse(lifecycle.claim(2L));          // somebody else already claimed it
    }

    @Test
    void successRecordsResultAndClearsErrorAndSchedule() {
        Task task = runningTask(1, 3);
        task.setLastError("old error");
        task.setNextRunAt(NOW);

        lifecycle.markSucceeded(1L, "done");

        assertEquals(TaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("done", task.getResult());
        assertNull(task.getLastError());
        assertNull(task.getNextRunAt());
        assertEquals(NOW, task.getFinishedAt());
    }

    @Test
    void failureWithAttemptsLeftGoesBackToPendingWithExponentialBackoff() {
        Task first = runningTask(1, 3);
        lifecycle.markAttemptFailed(1L, "boom");
        assertEquals(TaskStatus.PENDING, first.getStatus());
        assertEquals("boom", first.getLastError());
        assertEquals(NOW.plusSeconds(1), first.getNextRunAt());          // 1s after attempt 1

        Task second = runningTask(2, 3);
        lifecycle.markAttemptFailed(1L, "boom");
        assertEquals(NOW.plusSeconds(2), second.getNextRunAt());         // 2s after attempt 2
    }

    @Test
    void failureOnLastAttemptIsPermanent() {
        Task task = runningTask(3, 3);

        lifecycle.markAttemptFailed(1L, "boom");

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertNull(task.getNextRunAt());
        assertEquals(NOW, task.getFinishedAt());
    }

    @Test
    void lateResultFromAWorkerThatWasRequeuedMeanwhileIsIgnored() {
        Task task = runningTask(1, 3);
        task.setStatus(TaskStatus.PENDING);                              // recovered by recoverStale()

        lifecycle.markSucceeded(1L, "late result");

        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertNull(task.getResult());
    }

    @Test
    void recoverStaleRequeuesAndFailsAndReportsTheTotal() {
        when(repository.requeueStale(any(), any(), any(), any(), anyString())).thenReturn(2);
        when(repository.failStale(any(), any(), any(), any(), anyString())).thenReturn(1);

        assertEquals(3, lifecycle.recoverStale(Duration.ofMinutes(5)));
    }
}
