package com.example.task.controller;

import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskResponse;
import com.example.task.exception.InvalidTaskStateException;
import com.example.task.exception.TaskNotFoundException;
import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.model.TaskType;
import com.example.task.service.TaskExecutionService;
import com.example.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: routing, validation, status codes and JSON. Services are mocked - no database, no full context. */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private TaskExecutionService executionService;

    private static TaskResponse response(long id, TaskStatus status) {
        return new TaskResponse(id, "Send report", "weekly", TaskType.ECHO, null, status, 0, 3,
                Instant.parse("2026-09-21T10:00:00Z"), null, null, null, null, null);
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(response(1L, TaskStatus.PENDING));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Send report\",\"description\":\"weekly\",\"type\":\"ECHO\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tasks/1")))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.title", is("Send report")));
    }

    @Test
    void blankTitleIsRejectedWith400AndFieldDetails() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.details.title").exists());
        verifyNoInteractions(taskService);
    }

    @Test
    void maxAttemptsOutOfRangeIsRejected() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"maxAttempts\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.maxAttempts").exists());
    }

    @Test
    void unknownTaskTypeIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"type\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")));
    }

    @Test
    void listPassesTheStatusFilterAndDefaultPaging() throws Exception {
        when(taskService.list(TaskStatus.PENDING, 0, 50)).thenReturn(List.of(response(1L, TaskStatus.PENDING)));

        mockMvc.perform(get("/api/tasks").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)));
        verify(taskService).list(TaskStatus.PENDING, 0, 50);
    }

    @Test
    void pageSizeIsClampedToTheMaximum() throws Exception {
        when(taskService.list(null, 0, 200)).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks").param("size", "100000"))
                .andExpect(status().isOk());
        verify(taskService).list(null, 0, 200);
    }

    @Test
    void invalidStatusFilterIsA400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }

    @Test
    void unknownTaskIs404() throws Exception {
        when(taskService.get(5L)).thenThrow(new TaskNotFoundException(5L));

        mockMvc.perform(get("/api/tasks/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("TASK_NOT_FOUND")));
    }

    @Test
    void executeReturnsTheUpdatedTask() throws Exception {
        Task task = new Task();
        task.setId(3L);
        task.setTitle("t");
        task.setStatus(TaskStatus.SUCCEEDED);
        task.setType(TaskType.ECHO);
        when(executionService.executeNow(3L)).thenReturn(task);

        mockMvc.perform(post("/api/tasks/3/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));
    }

    @Test
    void executingATaskThatIsNotPendingIs409() throws Exception {
        when(executionService.executeNow(3L)).thenThrow(new InvalidTaskStateException("not PENDING"));

        mockMvc.perform(post("/api/tasks/3/execute"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_TASK_STATE")));
    }
}
