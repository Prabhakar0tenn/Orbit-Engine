package com.prabhakar.orbit.engine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import com.prabhakar.orbit.api.dto.SubmitJobRequest;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.service.JobService;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// Needs DynamoDB Local on port 8000 with the tables created; skipped otherwise.
@SpringBootTest
@DirtiesContext
class JobEngineEndToEndTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private DynamoDbClient client;

    private String jobId;

    @BeforeEach
    void requireDynamoDbLocal() {
        try {
            client.listTables();
        } catch (RuntimeException e) {
            assumeTrue(false, "DynamoDB Local is not running on port 8000");
        }
    }

    @AfterEach
    void cleanUp() {
        if (jobId != null) {
            client.deleteItem(b -> b.tableName("jobs").key(Map.of("jobId", AttributeValue.fromS(jobId))));
        }
    }

    @Test
    void aSubmittedJobIsPickedUpRunAndMarkedSucceeded() {
        Job submitted = jobService.submit(new SubmitJobRequest("e2e", "SLEEP", Map.of("ms", 50), null, null, null));
        jobId = submitted.getJobId();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(
                () -> assertEquals(JobStatus.SUCCEEDED, jobService.get(jobId).getStatus()));

        Job finished = jobService.get(jobId);
        assertEquals(1, finished.getAttempt());
        assertNull(finished.getLeaseOwner());
        assertNull(finished.getRunningMarker());
        assertNull(finished.getReadyShard());
    }
}
