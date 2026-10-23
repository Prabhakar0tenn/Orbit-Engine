# Context: Orbit-Engine (Job Scheduler & Workflow Engine)

Single source of truth for the assistant. Supersedes `description.md.txt` where they differ.
Detailed design lives in `Job_Scheduler_Project_Plan.md`; topic list in `time-table.txt`.

## 1. Who and why
- Developer: Prabhakar. Builds this for his resume (Amazon Programmer Analyst Intern application).
- Skills: Spring Boot basics (built one CRUD app, "stationery app"), has worked with Azure, never used JUnit/Mockito, new to AWS, DynamoDB and Java concurrency.
- This is a LEARNING project as much as a build. He must be able to explain every class in an interview.
- Time budget: about 2 weeks. At least 2 hours on most days, more on some. Assume roughly 35-40 hours total, with some days missed. Plan with buffer.
- Language: he reads English and Hinglish. Write docs in simple English.

## 2. What we are building
A backend service (Java 21, Spring Boot, Maven) that accepts jobs over REST, finds the ones that are due, runs them on a bounded thread pool, retries failures with exponential backoff, recovers jobs from crashed workers, stores state in DynamoDB and logs in S3. Deployed on AWS EC2 in Docker at the very end.

AWS comes LAST. Everything is built and tested locally first (DynamoDB Local in Docker, Testcontainers). Exception: create the AWS account, billing alarm and one real table early to avoid surprises at the end.

## 3. Environment (verified from the workspace)
- Project: `orbit-engine/` (Maven, artifactId `orbit-engine`, package `com.prabhakar.orbit`, main class `OrbitEngineApplication`). Renamed from `jobflow`; older docs still say jobflow.
- `pom.xml`: Java 21, Spring Boot parent 4.1.1 (NOT 3.x as the old plan says), starters: actuator, validation, webmvc; Lombok; Testcontainers (test).
- No AWS SDK, no JaCoCo, no Awaitility yet. They get added when first needed, not before.
- `application.yaml` only has `spring.application.name`.
- OS: Windows. Use PowerShell syntax in commands. Docker is NOT installed on this laptop (see decision 4).

### Decisions already made
- Java 21 (not 17 as the old plan says). Use records for DTOs, `switch` expressions where natural. No preview features.
- Package root is `com.prabhakar.orbit`.

### Confirmed decisions
1. Spring Boot stays on 4.1.1 (Spring Initializr no longer lists 3.5.x). Boot 4 means Spring Framework 7, Jackson 3 (`tools.jackson` packages), modular starters. Watch for this when following older tutorials.
2. Lombok: ONLY on `Job` and `Execution` (DynamoDB bean classes). Elsewhere use records/plain classes.
3. `LogStorage` interface with `LocalFileLogStorage` (dev/tests) and `S3LogStorage` (aws profile). No LocalStack.
4. No Docker on the dev laptop (a second PC has it). Local DynamoDB runs from the jar via `scripts/start-dynamodb-local.bat` (port 8000, `-sharedDb`). Integration tests use embedded/standalone DynamoDB Local instead of Testcontainers where possible (verify on Day 3). Testcontainers tests can run later in GitHub Actions or on the second PC.
5. AWS account exists (free plan, no card): $100 credits, ends 2027-04-20 or when credits run out (console default region ap-southeast-2). No resources created yet. Plan: smoke-test DynamoDB in CloudShell, deploy on Day 13, record a demo video because the account expires. Keep everything runnable locally. Fallback if a service is blocked: deploy elsewhere and describe it honestly on the resume.
6. Diagrams live in `Diagrams/` (HLD and LLD, Mermaid).

## 4. Tech stack (target)
- Java 21, Spring Boot, Maven
- AWS SDK v2: DynamoDB Enhanced Client, S3 (no Spring Cloud AWS)
- JUnit 5, Mockito, Testcontainers (DynamoDB Local), Awaitility, JaCoCo
- Docker (multi-stage), EC2 with IAM role (no hardcoded keys)

## 5. Architecture (unchanged from the plan)
- Layers: `api` -> `service` -> `repo`. All DynamoDB code only in `repo/`; S3 only in `storage/`.
- Engine: `JobPoller`, `WorkerPool`, `JobRunner`, `LeaseReaper`, `HeartbeatTask`.
- Flow: poller (every 1s) queries ready index -> atomic conditional claim -> runner executes handler with timeout -> conditional state update -> execution row + log.
- State machine: PENDING -> RUNNING -> SUCCEEDED / FAILED / RETRYING / CANCELLED. Terminal: SUCCEEDED, FAILED, CANCELLED.
- Tables: `jobs` (+ `ready-index`, `running-index`), `executions`, `idempotency_keys`. Full schema in the plan, section 5.

## 6. Critical rules (do not break)
- GSIs are eventually consistent: they suggest candidates only. The conditional write on the main table decides.
- Every state change is a conditional `UpdateItem`. `ConditionalCheckFailedException` = someone else won, skip quietly.
- `status` is reserved in DynamoDB: use `#status`.
- Times are UTC epoch millis. Inject `java.time.Clock`; never call `Instant.now()` directly in logic.
- Transitions validated in code (`JobStatus.canMoveTo`) and again by DB conditions.
- No `Thread.sleep` in tests: use Awaitility or a fake `Clock`.
- Never commit credentials. IAM role on EC2; env vars or `~/.aws` locally. `.gitignore` covers `.env`.
- At-least-once execution, so handlers must be idempotent. State this in the README.

## 7. Scope tiers (what we realistically build in ~2 weeks)

### Tier 1: MVP, must finish (this alone is a solid resume project)
- `JobStatus` state machine + parameterized tests
- `Job` model, DynamoDB tables + GSI, `JobRepository` (put/get/conditional update)
- `POST /jobs`, `GET /jobs/{id}` with validation and clean error JSON
- `WorkerPool` (ThreadPoolExecutor), `JobPoller`, atomic claim, `JobRunner`
- Handlers: `SLEEP`, `FLAKY`
- Retries with exponential backoff + full jitter, per-run timeout
- `executions` history + `GET /jobs/{id}/executions`
- Unit tests + Testcontainers integration tests, including the headline test: 50 jobs, 2 pollers, each runs exactly once
- README with architecture, schema, state diagram, trade-offs

### Tier 2: strong additions, build if on schedule
- Lease + heartbeat + `LeaseReaper` (crash recovery)
- Idempotent submit (`TransactWriteItems`)
- CRON / FIXED_RATE scheduling via Spring `CronExpression`
- Cancel API (cooperative for RUNNING)
- Logs to S3 (+ presigned URL)
- JaCoCo coverage report, `/stats`

### Tier 3: deployment and numbers (do not skip, but keep small)
- Dockerfile, DynamoDB + S3 + IAM role + EC2, `curl` verified
- Load test script and measured throughput (3 runs, median)

### Cut order if behind
`GENERATE_REPORT` -> `HTTP_CALL` -> `/stats` and metrics -> cancel of RUNNING jobs -> cron/fixed-rate -> write sharding (use N=1 and keep it configurable).
NEVER cut: state machine, atomic claim, retries, tests, EC2 deploy.

### Non-goals
UI, auth, multi-tenancy, Kafka/SQS, Kubernetes, DAG dependencies, extra libraries when JDK/Spring suffice.

## 8. Schedule (14 days, adjust to real hours)

| Day | Focus | Done when |
|---|---|---|
| 1 | Setup: Docker, DynamoDB Local, AWS account + billing alarm + CLI, run skeleton. Learn DynamoDB basics | `aws dynamodb list-tables --endpoint-url http://localhost:8000` works |
| 2 | `JobStatus` + JUnit tests, `Job` model, table creation | Tests green |
| 3 | AWS SDK config, `JobRepository` (put/get/conditional update), Testcontainers test | Save/load a job in DynamoDB Local |
| 4 | Submit/Get API, validation, `@RestControllerAdvice` (ONE_OFF) | `curl` submit and fetch works |
| 5 | `WorkerPool`, `JobPoller`, atomic claim | Poller claims a job |
| 6 | `JobRunner`, `SLEEP` handler, `executions` | First end-to-end: PENDING -> SUCCEEDED |
| 7 | Buffer / catch-up / commit and review | Everything so far explained back by Prabhakar |
| 8 | Backoff + `FLAKY` + timeout | FLAKY job goes RETRYING -> FAILED |
| 9 | Lease, heartbeat, `LeaseReaper` | Killed worker's job recovered |
| 10 | Idempotency, cron/fixed-rate, cancel | Each demoable with `curl` |
| 11 | `LogStorage` (local + S3), concurrency test (50 jobs, 2 pollers) | Exactly-once test green |
| 12 | Fill test gaps, JaCoCo, `/stats` | `mvn verify` green, coverage noted |
| 13 | Dockerfile, AWS resources, EC2 deploy | Live `curl` against EC2 |
| 14 | Load test, README, resume bullets, cleanup | Repo pushed, README complete |

Days 7 and 12 are the safety valves. If a day slips, consume buffer before touching Tier 3.

## 9. How to work with Prabhakar (teaching mode)
1. Before each step: name the topics he must know, in 1-3 lines, plus a search phrase for a short YouTube video if the topic is large. Do this BEFORE writing code.
2. Then: 2-3 lines on what we will build and why.
3. Then: code for the current step only, with short comments only where the code is not obvious. Explain JUnit/Mockito/concurrency pieces in plain words.
4. After: exact PowerShell commands to run and what output means success.
5. Where useful, let him write the small class himself (for example `BackoffPolicy`, `JobStatus`) and review it. He learns more, and he can defend it in interviews.
6. End each step with 2-3 "can you explain this?" questions tied to the interview Q&A in the plan.
7. Ask before design decisions that matter. Point out risks and mistakes honestly.
8. Azure analogies help him: Cosmos DB/Table Storage ~ DynamoDB, Blob ~ S3, VM ~ EC2, Managed Identity ~ IAM role, Durable Functions ~ Step Functions.

## 10. Conventions
- Constructor injection only. Small classes, clear names, no dead code, no over-engineering.
- Config via `@ConfigurationProperties` (`orbit.workers.*`, `orbit.poller.*`, `orbit.lease.*`, `aws.*`).
- Profiles: `local` (DynamoDB Local endpoint) and `aws`.
- HTTP errors via `@RestControllerAdvice`: 400 validation, 404 missing, 409 conflict.
- SLF4J logging including `jobId` and `attempt`.
- Resume numbers (throughput, coverage) must be MEASURED, never estimated.

## 11. Corrections to the older docs
- Java 21, not 17.
- Spring Boot is 4.1.1 in the generated pom (plan assumed 3.x). See open decision 1.
- Plan says 7 days at 3-4 h/day. Reality for a learner is 14 days at about 2.5 h/day; this file holds the adjusted schedule.
- Plan mentions LocalStack for S3; replaced by the `LogStorage` interface approach (open decision 3).

## 12. Current status
Day 1 done. DynamoDB Local runs via the .bat, AWS CLI 2.38.1 installed, tables `jobs` (+ `ready-index`, `running-index`), `executions`, `idempotency_keys` created with `scripts/create-tables.ps1`. Next: short hands-on Query/Scan/conditional-write exercise with the CLI, then Day 2 (`JobStatus` + JUnit tests, `Job` model).
(Update this line as we progress.)
