package com.example.task.service;

import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskResponse;
import com.example.task.exception.TaskNotFoundException;
import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.model.TaskType;
import com.example.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private TaskRepository repository;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TaskRepository.class);
        service = new TaskService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
    }

    @Test
    void createAppliesDefaultsAndMakesTheTaskDueImmediately() {
        TaskResponse response = service.create(new CreateTaskRequest("  Send report  ", "weekly", null, null, null));

        assertEquals(7L, response.id());
        assertEquals("Send report", response.title());                   // trimmed
        assertEquals(TaskType.ECHO, response.type());                    // default type
        assertEquals(TaskStatus.PENDING, response.status());
        assertEquals(0, response.attempts());
        assertEquals(3, response.maxAttempts());                         // default
        assertEquals(NOW, response.createdAt());
        assertEquals(NOW, response.nextRunAt());
    }

    @Test
    void createKeepsExplicitValues() {
        TaskResponse response = service.create(new CreateTaskRequest("x", null, TaskType.FAIL, "p", 5));

        assertEquals(TaskType.FAIL, response.type());
        assertEquals(5, response.maxAttempts());
        assertEquals("p", response.payload());
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> service.get(99L));
    }
}
