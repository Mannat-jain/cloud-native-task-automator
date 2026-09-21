package com.example.task.service;

import com.example.task.exception.InvalidTaskStateException;
import com.example.task.exception.TaskNotFoundException;
import com.example.task.handler.DelayTaskHandler;
import com.example.task.handler.EchoTaskHandler;
import com.example.task.handler.FailTaskHandler;
import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.model.TaskType;
import com.example.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskExecutionServiceTest {

    private TaskRepository repository;
    private TaskLifecycle lifecycle;
    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TaskRepository.class);
        lifecycle = Mockito.mock(TaskLifecycle.class);
        service = new TaskExecutionService(repository, lifecycle,
                List.of(new EchoTaskHandler(), new DelayTaskHandler(), new FailTaskHandler()));
    }

    private Task task(TaskType type) {
        Task task = new Task();
        task.setId(1L);
        task.setTitle("title");
        task.setDescription("hello");
        task.setType(type);
        task.setStatus(TaskStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    void successfulHandlerRecordsItsResult() {
        task(TaskType.ECHO);
        when(lifecycle.claim(1L)).thenReturn(true);

        service.executeNow(1L);

        verify(lifecycle).markSucceeded(1L, "hello");
        verify(lifecycle, never()).markAttemptFailed(any(), anyString());
    }

    @Test
    void throwingHandlerRecordsAFailedAttemptInsteadOfPropagating() {
        task(TaskType.FAIL);
        when(lifecycle.claim(1L)).thenReturn(true);

        service.executeNow(1L);                                          // must not throw

        verify(lifecycle).markAttemptFailed(any(), contains("IllegalStateException"));
        verify(lifecycle, never()).markSucceeded(any(), anyString());
    }

    @Test
    void taskThatCannotBeClaimedIsRejectedAndNeverRun() {
        task(TaskType.ECHO);
        when(lifecycle.claim(1L)).thenReturn(false);                     // already RUNNING / finished

        assertThrows(InvalidTaskStateException.class, () -> service.executeNow(1L));

        verify(lifecycle, never()).markSucceeded(any(), anyString());
    }

    @Test
    void unknownTaskIsReportedAsNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> service.executeNow(42L));
    }

    @Test
    void startupFailsFastWhenATaskTypeHasNoHandler() {
        assertThrows(IllegalStateException.class,
                () -> new TaskExecutionService(repository, lifecycle, List.of(new EchoTaskHandler())));
    }
}
