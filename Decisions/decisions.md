# Orbit-Engine: Decisions and Open Questions

Add an entry whenever we hit a design question or a "decide later" item.
Status: OPEN (not decided), DECIDED (chosen), REVISIT (check again later).

## Decided

| # | Decision | Why |
|---|---|---|
| D1 | Spring Boot stays on 4.1.1 | Spring Initializr no longer offers 3.5.x |
| D2 | Lombok only on `Job` and `Execution` | They have ~25 fields that the DynamoDB SDK needs getters and setters for |
| D3 | `LogStorage` interface with a local-file and an S3 version | Lets us build and test without AWS, then switch by config |
| D4 | No Docker on this laptop; tests use DynamoDB Local directly | Docker is not installed, and Docker is not needed until Day 13 |
| D5 | Project renamed to Orbit-Engine, package `com.prabhakar.orbit` | Cleaner name for the resume |
| D6 | Cloud region: keep `ap-southeast-2` unless there is a reason to change | Console already uses it; everything is local until Day 13 |
| D7 | `JobStatus` rules live in one method, `allowedNext()` | One source of truth: `canMoveTo` and `isTerminal` are derived from it |
| D8 | AWS account is a free plan (no card): $100 credits, ends 2027-04-20 | Record a demo video; keep the project runnable locally |
| D9 | Attributes are `jobName` and `jobType`, not `name` and `type` | `name` and `type` are DynamoDB reserved words |
| D10 | Enhanced Client for save/get/query; low-level `DynamoDbClient` for the claim | The claim needs a partial `SET ... REMOVE readyShard` conditional update that the Enhanced Client can't express as cleanly |
| D11 | Integration tests use the standalone DynamoDB Local on port 8000 and skip themselves if it is not running | No Docker; keeps `mvn test` green on any machine |
| D12 | Spring profile `local` is the default; `aws` is chosen explicitly later | Safe default, no accidental AWS calls |
| D13 | Credentials: dummy ones for DynamoDB Local; default AWS chain (profile `orbitengine`, env vars, EC2 role) for real AWS | No keys in code |
| D14 | Day 4 submit accepts only ONE_OFF jobs, with optional `delaySeconds`; supported types are the fixed set SLEEP, FLAKY until the handler registry exists | Keeps Day 4 small; cron arrives on Day 10 |
| D15 | No catch-all `Exception` handler in `GlobalExceptionHandler` | It would turn Spring's own 404/405 responses into 500s |
| D16 | `orbit.poller.shards` is 1 for now; shard is `READY#` + (jobId hash mod shards) | Resolves Q4: start with one shard, config-driven |
| D17 | Request payload is a JSON object, stored as a JSON string in `Job.payload` | Matches the DynamoDB design; Boot 4 uses Jackson 3 (`tools.jackson`) |

## Open (decide later)

| # | Question | Decide by | Notes |
|---|---|---|---|
| Q1 | What state does a RUNNING job end in when it is cancelled? | Day 10 | Today there is no RUNNING to CANCELLED move. Suggestion: add it, plus one line in `JobStatus`, and flip the test `RUNNING, CANCELLED` to allowed. |
| Q2 | When a CRON job fails after all retries, does it stay FAILED or go back to PENDING for the next cycle? | Day 10 | Pick one, write it in the README. |
| Q3 | RESOLVED (see D11): how integration tests reach DynamoDB | Day 3 | Standalone jar on port 8000. Revisit the embedded library only if we want CI. |
| Q4 | RESOLVED (see D16): write sharding for `ready-index` | Day 5 | Start with N=1. |
| Q5 | Should the DynamoDB tables be provisioned (small) or on-demand on AWS? | Day 13 | On-demand is simpler. Provisioned small capacity may be free. |
| Q6 | Fix the outdated lines in `Job_Scheduler_Project_Plan.md` (Java 17, LocalStack) or retire the file | Soon | `context.md` is the newer source of truth. |
| Q7 | Delete `description.md.txt` (replaced by `context.md`) | Soon | Waiting for Prabhakar's OK. |
| Q8 | RESOLVED: `ScheduleType` was created on Day 3 with the `Job` model | Day 3 | Done. |

## Learned (short notes)

- DynamoDB `status`, `type`, `name`, `key` are reserved words: use `#alias` in expressions.
- GSI is eventually consistent: it only suggests candidates; the conditional write on the main table decides.
- A conditional write that fails is not an error in our engine. It means another worker won.
- Claiming a job does not give exactly-once execution. It gives at-least-once, so handlers must be idempotent.
- A unit test for a rulebook (`JobStatus`) is different from tests for the classes that use it. Those get their own tests with mocks.
