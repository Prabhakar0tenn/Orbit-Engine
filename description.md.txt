# Project: jobflow (Job Scheduler & Workflow Engine)

## What we are building
A backend service in Java 21 + Spring Boot 3 that schedules and runs background jobs.
Think of it as a small, self-built version of Quartz / AWS Step Functions.

Clients submit jobs through a REST API. The engine finds jobs that are due, runs them on a
bounded thread pool, retries failures with exponential backoff, and recovers jobs if a worker
crashes. Job state lives in DynamoDB, execution logs live in S3, and the app is deployed on
AWS EC2 in Docker.

This is a learning and portfolio project for an Amazon Programmer Analyst Intern application.
It must show: schedulers, workflows / state machines, multi-threading, AWS (DynamoDB, S3, EC2),
REST web services, unit and integration tests, and clean engineering practice.
I (the developer) have never used JUnit/Mockito before, so explain test code briefly when you write it.

## Tech stack
- Java 17, Spring Boot 3.x, Maven
- AWS SDK v2: DynamoDB Enhanced Client, S3 (no Spring Cloud AWS)
- JUnit 5, Mockito, Testcontainers (DynamoDB Local), Awaitility, JaCoCo
- Docker (multi-stage build), AWS EC2 with an IAM role (never hardcode access keys)
- Package root: com.prabhakar.jobflow

## Core features (must-have)
1. Submit jobs via REST: ONE_OFF, CRON, FIXED_RATE.
2. Job state machine: PENDING -> RUNNING -> SUCCEEDED / FAILED / RETRYING / CANCELLED
   (PENDING also after a successful cron or fixed-rate run). Terminal states: SUCCEEDED, FAILED, CANCELLED.
3. Multi-threaded execution on a ThreadPoolExecutor (configurable core/max/queue, bounded queue, graceful shutdown).
4. Retries with exponential backoff and full jitter (Random is injected so tests are deterministic).
5. Idempotent submit using an idempotencyKey.
6. Lease + heartbeat + a LeaseReaper so jobs from crashed workers are recovered (at-least-once execution, so handlers must be idempotent).
7. Cancel API. Cancelling a RUNNING job is cooperative (cancelRequested flag + interrupt), never a force kill.
8. Status, execution history and log endpoints (logs via S3 presigned URLs).
9. Handlers: SLEEP, FLAKY (random failures), HTTP_CALL, GENERATE_REPORT (CSV uploaded to S3).

## Non-goals (do NOT add these)
UI, authentication, multi-tenancy, Kafka/SQS, Kubernetes, job dependencies/DAGs (stretch only),
extra libraries when the JDK or Spring already does the job (for example use Spring's CronExpression, not Quartz).
Prefer the simplest solution that works. No over-engineering.

## Architecture
Layers: api (controllers, DTOs, exception handler) -> service -> repo (all DynamoDB code lives here only).
Engine classes (JobPoller, WorkerPool, JobRunner, LeaseReaper, HeartbeatTask) use services and repos.
AWS SDK imports must not leak outside repo/ and storage/ packages.

Flow: JobPoller runs every 1s -> queries the ready GSI for due jobs (limited by free worker slots) ->
atomically claims each with a conditional update -> submits a JobRunner to the pool ->
the runner executes the handler with a timeout, updates state with conditional writes,
records an execution row, and uploads the log to S3.

## DynamoDB design (on-demand billing)
- Table `jobs` (PK jobId): name, type, payload (JSON string), scheduleType, cronExpr, fixedRateSec,
  status, nextRunAt (epoch ms), attempt, maxAttempts, backoffBaseMs, backoffCapMs, timeoutSec,
  leaseOwner, leaseExpiresAt, cancelRequested, version (optimistic lock), readyShard, runningMarker,
  lastError, lastLogKey, idempotencyKey, createdAt, updatedAt.
  - GSI `ready-index`: PK readyShard (e.g. READY#0..N-1, N=4, hash(jobId)%N), SK nextRunAt.
    Sparse: the attribute exists only while status is PENDING or RETRYING.
  - GSI `running-index`: PK runningMarker ("RUNNING"), SK leaseExpiresAt. Sparse: only while RUNNING.
- Table `executions` (PK jobId, SK "EXEC#<epochMillis>#<attempt>"): attempt, status, workerId,
  startedAt, finishedAt, durationMs, errorMessage, logS3Key.
- Table `idempotency_keys` (PK key): jobId, createdAt, ttl (DynamoDB TTL, 24h).
  Submit uses TransactWriteItems with attribute_not_exists on the key.

## Critical rules (do not break)
- GSIs are eventually consistent. They only suggest candidates. The source of truth is the conditional write on the main table.
- Every state change is a conditional UpdateItem (status + version, and leaseOwner for runner updates).
  ConditionalCheckFailedException means someone else won, so skip quietly.
- `status` is a DynamoDB reserved word, so always use an expression attribute name (#status).
- Store all times as UTC epoch millis. Inject java.time.Clock instead of calling Instant.now() directly.
- State transitions are validated in code (JobStatus.canMoveTo) and enforced again by DB conditions.
- No Thread.sleep in tests. Use Awaitility or a fake Clock.
- Never commit credentials. Use the IAM role on EC2, and env vars or ~/.aws locally.

## Testing expectations
- Unit tests: JobStatus transitions, BackoffPolicy, ScheduleCalculator, JobService (mocked repo),
  JobRunner (success, retry, exhausted, timeout, lease lost), JobPoller.
- Integration tests with Testcontainers + DynamoDB Local: end-to-end run, retry flow, lease recovery,
  idempotency, and the headline test: 50 jobs, 2 poller instances, every job executes exactly once.
- Target 70%+ line coverage on engine and service packages (JaCoCo).

## Conventions
- Constructor injection, no field injection. Small classes, meaningful names, no dead code.
- Config through @ConfigurationProperties (jobflow.workers.*, jobflow.poller.*, jobflow.lease.*, aws.*).
- Profiles: `local` (DynamoDB Local / LocalStack endpoints) and `aws` (real services).
- Proper HTTP errors via @RestControllerAdvice (400 validation, 404, 409).
- Log with SLF4J, include jobId and attempt in log lines.

## How I want you to work
- Build incrementally, one small piece at a time, following the day-wise plan below. Show code for the current step only.
- Before writing code, tell me in 2-3 lines what you will do and why. After, tell me how to run and verify it.
- If something is ambiguous or a design decision matters, ask me first instead of guessing.
- Explain concepts I might not know (JUnit, Mockito, ThreadPoolExecutor, conditional writes) in short plain language.
- Point out mistakes or risks in my approach honestly.

## Plan (7 days)
1. Skeleton, Docker DynamoDB Local, table scripts, JobStatus + tests, Job entity + repository.
2. Submit/get APIs, validation, idempotency, ScheduleCalculator.
3. WorkerPool, JobPoller, atomic claim, JobRunner, SLEEP handler, executions, S3 logs.
4. Retries/backoff, FLAKY, timeout, heartbeat, LeaseReaper, cron rescheduling, cancel, graceful shutdown, other handlers.
5. Unit + integration tests, JaCoCo, metrics, /stats.
6. Dockerfile, AWS setup (DynamoDB, S3, IAM role, EC2), load test.
7. README (architecture, schema, state diagram, trade-offs, load numbers), cleanup.

## Current status
Day 1 not started. (Update this line as we progress.)