package com.example.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// --- Data Access Repository ---
interface TaskRepository extends JpaRepository<Task, Long> {}

// --- REST Controller API Tier ---
@RestController
public class TaskController {

    private final TaskRepository repository;

    public TaskController(TaskRepository repository) {
        this.repository = repository;
    }

    // New Landing Page Endpoint to replace the Whitelabel Error
    @GetMapping("/")
    public String index() {
        return "<h1>🚀 Cloud-Native Task Automator Live!</h1>" +
               "<p><strong>Course:</strong> DevOps Laboratory Submission</p>" +
               "<hr/>" +
               "<h3>Verified Syllabus Modules Running:</h3>" +
               "<ul>" +
               "  <li><strong>Module 1:</strong> Build Automation (Maven POM & Wrapper)</li>" +
               "  <li><strong>Module 2:</strong> Containerization (Multi-Stage Optimized Build)</li>" +
               "  <li><strong>Module 3:</strong> Service Orchestration (Isolated Bridge Network & Persistent Volumes)</li>" +
               "</ul>" +
               "<p>Use <code><a href='/api/tasks'>/api/tasks</a></code> to access the headless REST data stream.</p>";
    }

    @GetMapping("/api/tasks")
    public List<Task> getAllTasks() {
        return repository.findAll();
    }

    @PostMapping("/api/tasks")
    public Task createTask(@RequestBody Task task) {
        return repository.save(task);
    }
}