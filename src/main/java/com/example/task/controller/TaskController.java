package com.example.task.controller;

import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskResponse;
import com.example.task.model.TaskStatus;
import com.example.task.service.TaskExecutionService;
import com.example.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** HTTP tier only: parse + validate input, delegate to the service layer, choose status codes. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TaskService taskService;
    private final TaskExecutionService executionService;

    public TaskController(TaskService taskService, TaskExecutionService executionService) {
        this.taskService = taskService;
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) TaskStatus status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return taskService.list(status, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return taskService.get(id);
    }

    /** Runs the task synchronously right now. 409 if it is not PENDING (already running / finished). */
    @PostMapping("/{id}/execute")
    public TaskResponse execute(@PathVariable Long id) {
        return TaskResponse.from(executionService.executeNow(id));
    }
}
