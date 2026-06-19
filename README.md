# 🚀 Cloud-Native Task Automator

[![Continuous Integration Pipeline](https://github.com/Mannat-jain/cloud-native-task-automator/actions/workflows/ci.yml/badge.svg)](https://github.com/Mannat-jain/cloud-native-task-automator/actions/workflows/ci.yml)
![Java Version](https://img.shields.io/badge/Java-17-blue)
![Spring Boot Version](https://img.shields.io/badge/Spring%20Boot-3.2.3-green)
![PostgreSQL Version](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker Compose](https://img.shields.io/badge/Docker%20Compose-Orchestrated-blue)

A headless, container-orchestrated Spring Boot microservice engineered with cloud-native design principles, strict network isolation, connection pool resiliency, and a robust QA testing framework. This project demonstrates backend architecture patterns and automation strategies suitable for production-grade deployments.

---

## 🗺️ System Architecture

This project is built using a decoupled multi-tier architecture, containerized with Docker, and orchestrated via Docker Compose. Below is a high-level representation of the request path, network boundary, and isolated database topology:

```mermaid
graph TD
    subgraph Host OS (Development / Production Host)
        subgraph Docker Compose Orchestration Boundary
            subgraph Private Bridge Network (backend_net)
                APP[Spring Boot API Container<br>task_app_container:8080] <-->|Internal JDBC - Port 5432| DB[(PostgreSQL Database<br>task_db_container)]
            end
            HOST_PORT[Host Port 5050] -->|Port Mapping| APP
        end
        CLIENT[REST Client / Browser] -->|HTTP Requests| HOST_PORT
    end

    style DB fill:#336791,stroke:#fff,stroke-width:2px,color:#fff
    style APP fill:#6db33f,stroke:#fff,stroke-width:2px,color:#fff
    style CLIENT fill:#e87333,stroke:#fff,stroke-width:2px,color:#fff
```

---

## 🛠️ Key Architectural Decisions & Engineering Complexity

### 1. Zero-Trust Database Isolation
To adhere to the principle of least privilege, the PostgreSQL database (`task_db_container`) is completely **isolated within a private Docker bridge network (`backend_net`)**.
* **Zero Host Exposures**: Unlike default setups, the PostgreSQL port `5432` is **not** exposed to the host machine.
* **Security Vector**: If the host machine is compromised, the database remains protected behind the Docker network boundary, reachable only by containerized services residing inside `backend_net` (the Spring Boot API).

### 2. High-Availability Resiliency (Connection Retries)
When orchestrating multi-container environments, a common race condition occurs: the application service boots faster than the database engine can initialize.
* **Resiliency Policy**: Standard configurations fail fast and crash the application. In `application.properties`, we configured:
  ```properties
  spring.datasource.hikari.initialization-fail-timeout=60000
  ```
* **Impact**: HikariCP is instructed to gracefully poll the database for up to 60 seconds. This prevents bootstrap crashes during cold starts or network lag, providing maximum deployment stability.

### 3. Lightweight Multi-Stage Containerization
To guarantee small production footprints and keep the attack surface minimal, the `Dockerfile` utilizes a multi-stage compilation flow:
* **Build Stage**: Compiles the source using `maven:3.9.6-eclipse-temurin-17-alpine` and downloads dependencies caching layers.
* **Runtime Stage**: Copies only the final compiled executable `.jar` into a clean, minimal `eclipse-temurin:17-jre-alpine` runtime. This strips out developer utilities, compiler tools, and temporary build caches, reducing container image size by over 60%.

---

## 🧪 QA & Testing Principles (Reliability and Verification)

A project is only as strong as its test suite. This application implements a strict, automated testing pipeline split into unit, integration, and continuous integration validations.

```
                  ┌───────────────────────────────┐
                  │      Push / Pull Request      │
                  └───────────────┬───────────────┘
                                  │
                                  ▼
                  ┌───────────────────────────────┐
                  │  GitHub Actions CI Triggered  │
                  └───────────────┬───────────────┘
                                  │
                                  ▼
                  ┌───────────────────────────────┐
                  │   Phase 1: Unit Testing       │
                  │   - WebMvcTest (TaskController)│
                  │   - Repositories Mocked       │
                  └───────────────┬───────────────┘
                                  │
                                  ▼
                  ┌───────────────────────────────┐
                  │  Phase 2: Integration Testing │
                  │   - SpringBootTest            │
                  │   - In-Memory H2 DB           │
                  │   - MockMvc End-to-End Flow   │
                  └───────────────┬───────────────┘
                                  │
                                  ▼
                  ┌───────────────────────────────┐
                  │ Phase 3: Docker Orchestration │
                  │   - Validate Compose Builds   │
                  └───────────────────────────────┘
```

### 1. Isolated Unit Testing
* **Strategy**: We test the REST Controller tier in isolation using `@WebMvcTest`. 
* **Implementation**: We mock `TaskRepository` using Mockito (`@MockBean`) and assert response structures and JSON patterns with `MockMvc` without initiating database drivers or loading the application context.
* **Key File**: [`TaskControllerTest.java`](file:///d:/Documents/code%20files/cloud-native-task-automator/src/test/java/com/example/task/TaskControllerTest.java)

### 2. End-to-End Integration Testing
* **Strategy**: Validate API handlers, Hibernate mappings, and transaction behavior working together in a mock database sandbox.
* **Implementation**: The application is tested with `@SpringBootTest` configured to run under the `@ActiveProfiles("test")`. This profile loads an isolated, in-memory **H2 database dialect** mimicking PostgreSQL.
* **State Cleansing**: A database setup hook runs `@BeforeEach` execution to truncate existing database tables, ensuring test assertions are completely idempotent and isolated.
* **Key File**: [`TaskControllerIntegrationTest.java`](file:///d:/Documents/code%20files/cloud-native-task-automator/src/test/java/com/example/task/TaskControllerIntegrationTest.java)

### 3. Continuous Integration (CI) Automation
The automated lifecycle is enforced via GitHub Actions (`ci.yml`):
1. **Source Code Checkout**: Pulls latest codebase.
2. **Java JDK 17 Setup**: Mounts the Temurin runtime and configures caching directories for Maven dependencies to keep build times under 45 seconds.
3. **Execution**: Performs `mvn clean verify` which runs both the unit tests and integration tests. If any assertion fails, the build is rejected.
4. **Compose Dry-Run**: Builds the multi-stage Docker environment to confirm Docker container configurations are functional.

---

## 🔌 API Reference & Specifications

The microservice exposes a headless REST API.

| Endpoint | Method | Description | Request Body | Response Format |
| :--- | :--- | :--- | :--- | :--- |
| `/` | `GET` | Health Check & System Status Landing Page | None | HTML (Status Board) |
| `/api/tasks` | `GET` | Fetches all task payloads from PostgreSQL database | None | JSON Array |
| `/api/tasks` | `POST` | Commits a new task automation payload to the database | Task JSON | JSON Object (Created Task) |

### Sample Payloads

#### Create a Task
**Request**: `POST /api/tasks`
```json
{
  "title": "Deploy Production Backup",
  "description": "Automate database snapshots every night at 02:00 AM."
}
```

**Response**: `200 OK`
```json
{
  "id": 1,
  "title": "Deploy Production Backup",
  "description": "Automate database snapshots every night at 02:00 AM."
}
```

#### Retrieve Tasks
**Request**: `GET /api/tasks`

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "title": "Deploy Production Backup",
    "description": "Automate database snapshots every night at 02:00 AM."
  }
]
```

---

## 🚀 Running the Project

### Prerequisites
* Docker & Docker Compose installed on your host machine.
* Java Development Kit (JDK) 17 and Maven (if running bare-metal locally).

### Method 1: Docker Compose Orchestration (Recommended)
This runs the entire stack inside container boundaries matching production profiles:
```bash
# 1. Clone the repository and navigate to root
cd cloud-native-task-automator

# 2. Compile, construct the Docker images, and launch the isolated topology
docker compose up --build
```
Once spun up, the API will be mapped and reachable on `http://localhost:5050/`.

### Method 2: Local Development (Bare-Metal)
You can run the API locally with a local PostgreSQL server instance or using the H2 testing profile.
```bash
# 1. Run full unit and integration test suite
mvn clean verify

# 2. Run the application locally
mvn spring-boot:run
```

---

## 🚦 Verification Checkpoints
To confirm the services are executing as expected:
1. **Landing Page**: Navigate to `http://localhost:5050/` in a web browser. You should see the **Cloud-Native Task Automator Live!** status board indicating successful build automation, containerization, and network status.
2. **API Read Test**: Send a `GET` request to `http://localhost:5050/api/tasks`. It should return a `200 OK` status and a valid JSON response (empty array `[]` initially).
3. **Database Write Test**: Send a `POST` request to `http://localhost:5050/api/tasks` with a valid JSON payload. Verify that the response includes a generated database `id` field.
