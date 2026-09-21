package com.example.task.service;

import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskResponse;
import com.example.task.exception.TaskNotFoundException;
import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.model.TaskType;
import com.example.task.repository.TaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Business rules for creating and querying tasks. The controller only maps HTTP to these calls. */
@Service
public class TaskService {

    static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final TaskRepository repository;
    private final Clock clock;

    public TaskService(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Instant now = clock.instant();
        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setType(request.type() != null ? request.type() : TaskType.ECHO);
        task.setPayload(request.payload());
        task.setMaxAttempts(request.maxAttempts() != null ? request.maxAttempts() : DEFAULT_MAX_ATTEMPTS);
        task.setStatus(TaskStatus.PENDING);
        task.setAttempts(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setNextRunAt(now);                                        // due immediately
        return TaskResponse.from(repository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return repository.findById(id).map(TaskResponse::from).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        List<Task> tasks = status == null
                ? repository.findAll(pageable).getContent()
                : repository.findByStatus(status, pageable);
        return tasks.stream().map(TaskResponse::from).toList();
    }
}
