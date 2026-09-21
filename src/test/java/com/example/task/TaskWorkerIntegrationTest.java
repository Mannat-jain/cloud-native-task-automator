package com.example.task;

import com.example.task.dto.CreateTaskRequest;
import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import com.example.task.model.TaskType;
import com.example.task.repository.TaskRepository;
import com.example.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The background worker end to end: tasks are created through the service and nobody calls /execute - the
 * scheduled poller must claim and run them, retrying failures with backoff. Uses its own in-memory database.
 */
@SpringBootTest(properties = {
        "app.worker.enabled=true",
        "app.worker.initial-delay-ms=0",
        "app.worker.poll-interval-ms=100",
        "app.retry.base-delay-ms=100",
        "spring.datasource.url=jdbc:h2:mem:workerdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@ActiveProfiles("test")
class TaskWorkerIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository repository;

    private Task awaitStatus(Long id, TaskStatus expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Task task = repository.findById(id).orElseThrow();
            if (task.getStatus() == expected) {
                return task;
            }
            Thread.sleep(50);
        }
        Task last = repository.findById(id).orElseThrow();
        return fail("Task " + id + " did not reach " + expected + " in time, last status was " + last.getStatus());
    }

    @Test
    void workerPicksUpAndRunsPendingTasksWithoutAnyApiCall() throws Exception {
        Long id = taskService.create(new CreateTaskRequest("auto", "processed by the worker", TaskType.ECHO, null, null)).id();

        Task done = awaitStatus(id, TaskStatus.SUCCEEDED);

        assertEquals(1, done.getAttempts());
        assertEquals("processed by the worker", done.getResult());
        assertNotNull(done.getFinishedAt());
    }

    @Test
    void workerRetriesAFailingTaskAndEventuallyMarksItFailed() throws Exception {
        Long id = taskService.create(new CreateTaskRequest("flaky", null, TaskType.FAIL, null, 2)).id();

        Task failed = awaitStatus(id, TaskStatus.FAILED);

        assertEquals(2, failed.getAttempts());                           // retried once, then gave up
        assertNotNull(failed.getLastError());
    }
}
