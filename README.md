# Cloud-Native Task Automator

[![CI](https://github.com/Mannat-jain/cloud-native-task-automator/actions/workflows/ci.yml/badge.svg)](https://github.com/Mannat-jain/cloud-native-task-automator/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker Compose](https://img.shields.io/badge/Docker%20Compose-orchestrated-blue)

A Spring Boot REST service for creating tasks and executing them **reliably**: a background worker picks tasks up,
runs them on a bounded thread pool, retries failures with exponential backoff, and never runs the same task twice.
PostgreSQL 16 for persistence, HikariCP for pooling, Docker Compose for orchestration, GitHub Actions for CI.

It is one deployable service (plus its database), not a distributed system of many services - the design goal is a
small codebase that demonstrates production habits: layering, validation, error handling, safe concurrency,
retries, tests, containers and CI.

---

## Architecture

```
                 HTTP                                   +---------------------------+
 client ───────────────►  TaskController                |  TaskWorker (@Scheduled)  |
                          (validate, status codes)      |  every 2 s: find due tasks|
                                 │                      |  -> claim -> thread pool  |
                                 ▼                      +-------------┬-------------+
                          TaskService                                 │
                          (create / query)              TaskExecutionService  ── TaskHandler (Strategy)
                                 │                      (runs handler OUTSIDE a transaction)   ECHO / DELAY / FAIL
                                 │                                    │
                                 └──────────────┬─────────────────────┘
                                                ▼
                                          TaskLifecycle  (claim / succeed / fail+retry / recover; short transactions)
                                                ▼
                                          TaskRepository (Spring Data JPA)  ──►  PostgreSQL 16  (HikariCP)
```

| Layer | Package | Responsibility (and what it must NOT do) |
|---|---|---|
| Controller | `controller` | HTTP only: parse/validate input, choose status codes. No business logic, no DB access. |
| Service | `service` | Business rules, transactions, orchestration. No HTTP types. |
| Repository | `repository` | Persistence only (Spring Data JPA + a few precise JPQL updates). |
| Model / DTO | `model`, `dto` | JPA entity vs. request/response records - the entity is never exposed over HTTP. |
| Errors | `exception` | One `@RestControllerAdvice` maps exceptions to a uniform JSON error body. |
| Handlers | `handler` | One `TaskHandler` per task type (Strategy pattern). Add a type = enum constant + one class. |

## Task lifecycle

```
 PENDING ──claim──► RUNNING ──success──────────────────────────► SUCCEEDED
    ▲                  │
    │                  ├─ failure, attempts left ─► PENDING  (next_run_at = now + 2s, 4s, 8s ... capped)
    │                  └─ failure, no attempts left ─► FAILED
    └── a task stuck in RUNNING (worker died) is requeued after 5 min, or FAILED if it has no attempts left
```

### Why a task can never run twice (concurrency)
Claiming is **one conditional `UPDATE`**:

```sql
UPDATE tasks SET status = 'RUNNING', attempts = attempts + 1, ... WHERE id = ? AND status = 'PENDING'
```

The status check and the change happen in a single statement, so if two workers (or two application instances) race
for the same task exactly one gets `1` row updated and the other gets `0` - there is no read-then-write window.
A worker only runs a task if it won the claim. `TaskRepositoryTest` pins this behaviour (second claim returns 0).
Execution is **at-least-once** (a crashed worker's task is retried), so handlers should be idempotent.
At higher scale the next steps are `SELECT ... FOR UPDATE SKIP LOCKED` or a real queue (SQS/RabbitMQ/Kafka).

Handlers run **outside** any transaction, and state changes happen in short transactions in a separate bean
(`TaskLifecycle`): a slow task therefore never holds a database connection, and `@Transactional` is not bypassed
by self-invocation.

---

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/tasks` | Create a task (`201` + `Location`). Body: `title` (required), `description`, `type` (`ECHO`\|`DELAY`\|`FAIL`, default `ECHO`), `payload`, `maxAttempts` (1-10, default 3). |
| `GET` | `/api/tasks?status=&page=&size=` | List tasks, newest first (`size` max 200). |
| `GET` | `/api/tasks/{id}` | One task, including `status`, `attempts`, `nextRunAt`, `result`, `lastError`. |
| `POST` | `/api/tasks/{id}/execute` | Run a `PENDING` task **now**, synchronously. `409` if it is not `PENDING`. |
| `GET` | `/actuator/health` | Health (includes the DB check); used by the Docker healthcheck. |

Errors always look like `{"status":400,"code":"VALIDATION_FAILED","message":"...","details":{"title":"..."},"timestamp":"..."}`
(`400` validation/malformed input, `404` unknown task, `409` wrong state).

```bash
curl -s -X POST localhost:5050/api/tasks -H 'Content-Type: application/json' \
     -d '{"title":"Say hello","description":"hello world","type":"ECHO"}'
# the background worker runs it within ~2-5 s:
curl -s localhost:5050/api/tasks/1
# see retries + backoff in action:
curl -s -X POST localhost:5050/api/tasks -H 'Content-Type: application/json' \
     -d '{"title":"flaky","type":"FAIL","maxAttempts":3}'
```

---

## Run it

```bash
cp .env.example .env            # set POSTGRES_PASSWORD (the file is git-ignored)
docker compose up -d --build    # API on http://localhost:5050
docker compose logs -f app
docker compose down             # add -v to also delete the database volume
```

> **Upgrading from the first version?** The `tasks` table gained columns (status, attempts, ...). Run
> `docker compose down -v` once to drop the old volume - `ddl-auto=update` cannot add NOT NULL columns to existing rows.

Without Docker: start a local Postgres, then
`SPRING_DATASOURCE_PASSWORD=... mvn spring-boot:run` (URL/username default to `localhost:5432/taskdb` / `taskuser`).

## Configuration highlights (`application.properties`, all overridable by environment variables)

| Area | Setting | Why |
|---|---|---|
| HikariCP | `maximum-pool-size=10`, `minimum-idle=2` | A small pool + queueing beats hundreds of connections thrashing Postgres. |
| HikariCP | `connection-timeout=30000`, `max-lifetime=1800000` | Fail a request that waits too long; retire connections before a firewall drops them. |
| HikariCP | `leak-detection-threshold=20000` | Logs a warning when a connection is held > 20 s (leaks, long transactions). |
| HikariCP | **`initialization-fail-timeout=60000`** | **60-second readiness window**: the pool keeps retrying at start-up instead of crashing while Postgres boots. |
| JPA | `open-in-view=false`, `ddl-auto=update` | No connection held for the whole request. `update` keeps the demo zero-setup; production should use `validate` + Flyway. |
| Worker | `app.worker.*`, `app.retry.*` | Poll interval, batch size, threads (4), queue (100), stale timeout (300 s), backoff base (2 s) and cap (5 min). |

## Containers

* **Multi-stage `Dockerfile`** - Maven/JDK only in the build stage; the runtime image is a JRE-only Alpine image,
  runs as a **non-root** user, and sizes the heap from the container limit (`MaxRAMPercentage`). The pom is copied
  first so the dependency layer is cached until `pom.xml` changes.
* **`docker-compose.yml`** - two networks:
  * `frontend_net` - the only one that publishes a port (API on `:5050`);
  * `backend_net` - `internal: true` (no route to the host or the internet), shared by the API and Postgres.
  Postgres has **no `ports:` mapping**, so it is reachable only from the API container.
* **Readiness** - Postgres has a `pg_isready` healthcheck and the API uses `depends_on: condition: service_healthy`
  (plain `depends_on` only waits for the container to *start*, not for the database to accept connections). The
  HikariCP window above is a second, independent safety net. The API also has a healthcheck on `/actuator/health`.
* **Secrets** come from `.env` (git-ignored, template in `.env.example`); nothing sensitive is committed.

## Tests (`mvn clean verify`)

| Test | Kind | What it proves |
|---|---|---|
| `RetryPolicyTest`, `DelayTaskHandlerTest` | unit | 1s/2s/4s backoff, cap without overflow, payload parsing |
| `TaskServiceTest`, `TaskLifecycleTest`, `TaskExecutionServiceTest` | unit (Mockito) | defaults, state transitions, retry vs. permanent failure, ignoring a stale worker's late result, fail-fast on a missing handler |
| `TaskControllerTest` | `@WebMvcTest` | status codes (201/400/404/409), validation messages, paging clamp - services mocked |
| `TaskRepositoryTest` | `@DataJpaTest` | the JPQL: claim succeeds once then loses, due-task ordering/limit, stale recovery |
| `TaskWorkflowIntegrationTest` | `@SpringBootTest` + MockMvc + H2 | create -> execute -> read back, retries then `FAILED`, `409` on re-execution, health endpoint |
| `TaskWorkerIntegrationTest` | `@SpringBootTest` | the scheduled worker runs and retries tasks with **no API call** |

The test profile uses H2 in PostgreSQL mode. H2 is not Postgres (dialect, locking and isolation differ), so the next
upgrade would be **Testcontainers** with a real `postgres:16` for the repository/integration tests.

## CI (`.github/workflows/ci.yml`)

On every push/PR: set up Java 17 with **Maven cache** -> `mvn -B clean verify` (compile, unit + integration tests,
package) -> upload Surefire reports on failure -> `docker compose config` + `docker compose build` to prove the
container build still works. Suggested additions: Docker image push, JaCoCo coverage gate, Dependabot, Trivy scan.

## Known limitations

* No authentication/authorisation on the API.
* `ddl-auto=update` instead of migrations (Flyway/Liquibase).
* Task types are simulated (`ECHO`, `DELAY`, `FAIL`); real handlers (HTTP calls, emails, ...) plug in through `TaskHandler`.
* At-least-once execution: handlers must be idempotent.
* Stale-task recovery uses a fixed timeout; long-running handlers would need a heartbeat.
