# Orbit-Engine: Day-wise Plan

Update the checkboxes as we go. Days can slip; buffer days (7 and 12) absorb that.

## Progress

```
Days    [####----------]  4 / 14 done
Tier 1  [##------------]  MVP (must finish)
Tier 2  [--------------]  Lease, cron, cancel, idempotency, S3 logs
Tier 3  [--------------]  AWS deploy + load test
```

| Milestone | Status |
|---|---|
| Local DynamoDB running | Done |
| Tables + GSIs created | Done |
| `JobStatus` + tests | Done |
| Repository + REST API | Done (Days 3-4) |
| Engine (poller, pool, runner) | Next (Day 5) |
| Retries, lease, recovery | Not started |
| Tests + coverage | Not started |
| AWS deploy + load test | Not started |
| README + resume bullets | Not started |

---

## Day 1: Setup and DynamoDB basics (DONE)
**Learned:** tables, items, partition/sort keys, Query vs Scan, GSI, sparse index, eventual consistency, conditional writes.
- [x] DynamoDB Local running (`scripts/start-dynamodb-local.bat`)
- [x] AWS CLI installed
- [x] Tables `jobs`, `executions`, `idempotency_keys` + `ready-index`, `running-index`
- [x] Hands-on: Query GSI, Scan, conditional claim, failed second claim
- [x] Diagrams saved in `Diagrams/`

## Day 2: JobStatus + JUnit (DONE)
**Learn first:** JUnit 5 basics (`@Test`, `assertEquals`, `assertThrows`, `@ParameterizedTest`), Java enums with methods.
**Build:**
- [x] Package structure under `com.prabhakar.orbit`
- [x] `JobStatus` enum with allowed transitions (`canMoveTo`, `isTerminal`)
- [x] `JobStatusTest`: every allowed and disallowed transition (27 runs, `mvn test` green with 28 total)
- [ ] `ScheduleType` enum (moved to Day 3, created with the `Job` model)
**Done when:** `mvn test` is green and you can explain the state machine diagram.

## Day 3: Repository and DynamoDB from Java (DONE, pending your explanation review)
**Learn first:** AWS SDK v2 Enhanced Client basics, `@DynamoDbBean`, `@DynamoDbPartitionKey`.
**Build:**
- [x] AWS SDK BOM + `dynamodb-enhanced` in the pom, `AwsProperties`, `AwsConfig`, `local` profile
- [x] `Job` model (Lombok) and `ScheduleType`
- [x] `JobRepository`: save, findById, findReady (GSI), conditional claim
- [x] Integration test against DynamoDB Local (3 tests; 31 total pass)
**Done when:** a job is saved and read back from Java, and a second claim fails.

## Day 4: REST API (DONE, pending your explanation review)
**Learn first:** Bean Validation (`@Valid`, `@NotNull`), `@RestControllerAdvice`, HTTP status codes.
**Build:**
- [x] `POST /api/v1/jobs`, `GET /api/v1/jobs/{id}` (ONE_OFF jobs, optional `delaySeconds`)
- [x] Request/response records, validation, clean error JSON (400, 404)
- [x] `JobService` + Mockito tests, `@WebMvcTest` controller tests (42 tests total, 39 run without DynamoDB Local)
**Done when:** `curl` submit and fetch both work. (Verified against the running app.)

## Day 5: Worker pool, poller, atomic claim
**Learn first:** race conditions, `ThreadPoolExecutor` (core/max/queue/rejection), `@Scheduled`.
**Build:**
- [ ] `WorkerPool` (bounded queue, named threads)
- [ ] `JobPoller` (query ready-index, limit by free slots, claim)
- [ ] `JobHandler` interface + registry
**Done when:** the poller claims a job and hands it to a worker thread.

## Day 6: JobRunner, SLEEP handler, run history
**Learn first:** `Runnable` vs `Callable`, `Future`, `java.time.Clock`.
**Build:**
- [ ] `JobRunner`, `SleepHandler`
- [ ] `ExecutionRepository`, `GET /jobs/{id}/executions`
- [ ] Conditional state updates (RUNNING to SUCCEEDED)
**Done when:** first end-to-end run: PENDING to SUCCEEDED, with history.

## Day 7: Buffer, review, commit
- [ ] Catch up on anything unfinished
- [ ] Re-read all code; explain each class out loud
- [ ] Git commit with clear messages
**Decision gate:** if the core engine does not run end to end, we stop adding features and fix that.

## Day 8: Retries, backoff, timeout
**Learn first:** exponential backoff and jitter, `Future.get(timeout)`, `Thread.interrupt()`.
**Build:**
- [ ] `BackoffPolicy` (seeded `Random` for tests)
- [ ] `FlakyHandler`
- [ ] Retry flow: RUNNING to RETRYING to FAILED, timeout handling
**Done when:** a FLAKY job retries with growing delays, then fails after max attempts.

## Day 9: Lease, heartbeat, reaper
**Learn first:** lease and heartbeat idea, `ScheduledExecutorService`.
**Build:**
- [ ] Lease on claim, `HeartbeatTask`
- [ ] `LeaseReaper` using `running-index`
- [ ] Graceful shutdown
**Done when:** a "killed" worker's job is recovered and re-run.

## Day 10: Idempotency, cron, cancel
**Learn first:** `TransactWriteItems`, DynamoDB TTL, Spring `CronExpression`.
**Build:**
- [ ] Idempotent submit
- [ ] CRON and FIXED_RATE (`ScheduleCalculator`)
- [ ] Cancel API (cooperative for RUNNING)
**Also:** AWS free-plan account is ready (done). Re-check on this day that DynamoDB, S3 and EC2 are allowed on the free plan.

## Day 11: LogStorage and the headline test
**Learn first:** S3 basics, Awaitility.
**Build:**
- [ ] `LogStorage`: `LocalFileLogStorage` and `S3LogStorage`
- [ ] Concurrency test: 50 jobs, 2 pollers, each runs exactly once

## Day 12: Tests, coverage, buffer
- [ ] Fill test gaps (runner, poller, service)
- [ ] JaCoCo report, note the coverage number
- [ ] `/stats` endpoint if time allows

## Day 13: Deploy
**Learn first:** IAM role vs user, EC2, security groups.
- [ ] Dockerfile (or run the jar directly on EC2)
- [ ] DynamoDB tables, S3 bucket, IAM role, EC2 on AWS
- [ ] `curl` against the live instance
**Fallback:** if there is no AWS account, deploy elsewhere and describe it honestly.

## Day 14: Load test and README
- [ ] Load test (3 runs, median), compare 4/8/16 workers
- [ ] README: architecture, schema, state diagram, trade-offs, how to run, numbers
- [ ] Resume bullets with measured numbers, 30-second pitch
- [ ] Stop EC2, record demo video

---

## Cut order if behind
`GENERATE_REPORT` -> `HTTP_CALL` -> `/stats` -> cancel of RUNNING jobs -> cron/fixed-rate -> sharding.
**Never cut:** state machine, atomic claim, retries, tests, deploy.
