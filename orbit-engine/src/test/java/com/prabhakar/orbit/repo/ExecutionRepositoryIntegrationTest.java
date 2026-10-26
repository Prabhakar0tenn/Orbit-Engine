package com.prabhakar.orbit.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.prabhakar.orbit.domain.Execution;
import com.prabhakar.orbit.domain.ExecutionStatus;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// Needs DynamoDB Local running on port 8000 with the tables created; skipped otherwise.
@SpringBootTest(properties = "orbit.poller.enabled=false")
class ExecutionRepositoryIntegrationTest {

    @Autowired
    private ExecutionRepository repository;

    @Autowired
    private DynamoDbClient client;

    private String jobId;
    private final List<String> sortKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        try {
            client.listTables();
        } catch (RuntimeException e) {
            assumeTrue(false, "DynamoDB Local is not running on port 8000");
        }
        jobId = UUID.randomUUID().toString();
    }

    @AfterEach
    void cleanUp() {
        for (String sortKey : sortKeys) {
            client.deleteItem(b -> b.tableName("executions")
                    .key(Map.of("jobId", AttributeValue.fromS(jobId), "sk", AttributeValue.fromS(sortKey))));
        }
    }

    @Test
    void returnsTheNewestRunFirstAndHonoursTheLimit() {
        saveRun(1, 1_000L);
        saveRun(2, 2_000L);
        saveRun(3, 3_000L);

        List<Execution> latestTwo = repository.findLatest(jobId, 2);

        assertEquals(2, latestTwo.size());
        assertEquals(3, latestTwo.get(0).getAttempt());
        assertEquals(2, latestTwo.get(1).getAttempt());
    }

    @Test
    void savingTheSameRunAgainOverwritesIt() {
        Execution run = saveRun(1, 1_000L);
        run.setStatus(ExecutionStatus.SUCCEEDED);
        run.setFinishedAt(1_200L);
        run.setDurationMs(200L);

        repository.save(run);

        List<Execution> history = repository.findLatest(jobId, 10);
        assertEquals(1, history.size());
        assertEquals(ExecutionStatus.SUCCEEDED, history.get(0).getStatus());
        assertEquals(200L, history.get(0).getDurationMs());
    }

    @Test
    void anUnknownJobHasNoHistory() {
        assertTrue(repository.findLatest(jobId, 10).isEmpty());
    }

    private Execution saveRun(int attempt, long startedAt) {
        Execution run = new Execution();
        run.setJobId(jobId);
        run.setSk(Execution.sortKey(startedAt, attempt));
        run.setAttempt(attempt);
        run.setStatus(ExecutionStatus.RUNNING);
        run.setWorkerId("worker-A");
        run.setStartedAt(startedAt);
        repository.save(run);
        sortKeys.add(run.getSk());
        return run;
    }
}
