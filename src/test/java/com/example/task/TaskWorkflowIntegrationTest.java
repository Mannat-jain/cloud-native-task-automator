package com.example.task;

import com.example.task.repository.TaskRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack (controller -> service -> lifecycle -> JPA -> H2) through HTTP, worker switched off (test profile). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private long createTask(String json) throws Exception {
        String body = mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void createExecuteAndReadBackASuccessfulTask() throws Exception {
        long id = createTask("{\"title\":\"Say hello\",\"description\":\"hello world\",\"type\":\"ECHO\"}");

        mockMvc.perform(get("/api/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.attempts", is(0)));

        mockMvc.perform(post("/api/tasks/" + id + "/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")))
                .andExpect(jsonPath("$.attempts", is(1)))
                .andExpect(jsonPath("$.result", is("hello world")))
                .andExpect(jsonPath("$.finishedAt", notNullValue()));

        mockMvc.perform(get("/api/tasks").param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void failingTaskIsRetriedThenMarkedFailedAndCannotBeRunAgain() throws Exception {
        long id = createTask("{\"title\":\"Always fails\",\"type\":\"FAIL\",\"maxAttempts\":2}");

        mockMvc.perform(post("/api/tasks/" + id + "/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))                       // attempt 1 of 2 failed -> retry later
                .andExpect(jsonPath("$.attempts", is(1)))
                .andExpect(jsonPath("$.nextRunAt", notNullValue()))
                .andExpect(jsonPath("$.lastError", containsString("Simulated failure")));

        mockMvc.perform(post("/api/tasks/" + id + "/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))                        // no attempts left
                .andExpect(jsonPath("$.attempts", is(2)));

        mockMvc.perform(post("/api/tasks/" + id + "/execute"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_TASK_STATE")));
    }

    @Test
    void executingAnAlreadyFinishedTaskIsAConflict() throws Exception {
        long id = createTask("{\"title\":\"once\"}");
        mockMvc.perform(post("/api/tasks/" + id + "/execute")).andExpect(status().isOk());

        mockMvc.perform(post("/api/tasks/" + id + "/execute")).andExpect(status().isConflict());
    }

    @Test
    void unknownTaskIs404() throws Exception {
        mockMvc.perform(get("/api/tasks/424242")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tasks/424242/execute")).andExpect(status().isNotFound());
    }

    @Test
    void invalidInputIsRejectedWithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.details.title", notNullValue()));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
