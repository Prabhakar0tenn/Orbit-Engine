package com.prabhakar.orbit.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// Needs DynamoDB Local running on port 8000 with the tables created; skipped otherwise.
@SpringBootTest
class JobRepositoryIntegrationTest {

    @Autowired
    private JobRepository repository;

    @Autowired
    private DynamoDbClient client;

    private final List<String> createdJobIds = new ArrayList<>();
    private String shard;

    @BeforeEach
    void setUp() {
        try {
            client.listTables();
        } catch (RuntimeException e) {
            assumeTrue(false, "DynamoDB Local is not running on port 8000");
        }
        shard = "TEST#" + UUID.randomUUID();
    }

    @AfterEach
    void cleanUp() {
        for (String jobId : createdJobIds) {
            client.deleteItem(b -> b.tableName("jobs").key(Map.of("jobId", AttributeValue.fromS(jobId))));
        }
    }

    @Test
    void savesAndLoadsAJob() {
        Job job = pendingJob(1000L);

        repository.save(job);
        Job loaded = repository.findById(job.getJobId()).orElseThrow();

        assertEquals(JobStatus.PENDING, loaded.getStatus());
        assertEquals(shard, loaded.getReadyShard());
        assertEquals(1000L, loaded.getNextRunAt());
    }

    @Test
    void findReadyReturnsOnlyDueJobs() {
        Job due = pendingJob(1000L);
        Job notDue = pendingJob(9000L);
        repository.save(due);
        repository.save(notDue);

        List<Job> ready = repository.findReady(shard, 5000L, 10);

        assertEquals(1, ready.size());
        assertEquals(due.getJobId(), ready.get(0).getJobId());
    }

    @Test
    void onlyOneClaimWinsAndJobLeavesTheReadyIndex() {
        Job job = pendingJob(1000L);
        repository.save(job);

        boolean first = repository.claim(job, "worker-A", 5000L, 30_000L);
        boolean second = repository.claim(job, "worker-B", 5000L, 30_000L);

        assertTrue(first);
        assertFalse(second);
        Job claimed = repository.findById(job.getJobId()).orElseThrow();
        assertEquals(JobStatus.RUNNING, claimed.getStatus());
        assertEquals("worker-A", claimed.getLeaseOwner());
        assertEquals(2L, claimed.getVersion());
        assertNull(claimed.getReadyShard());
        assertTrue(repository.findReady(shard, 5000L, 10).isEmpty());
    }

    private Job pendingJob(long nextRunAt) {
        Job job = new Job();
        job.setJobId(UUID.randomUUID().toString());
        job.setJobName("test-job");
        job.setJobType("SLEEP");
        job.setStatus(JobStatus.PENDING);
        job.setNextRunAt(nextRunAt);
        job.setReadyShard(shard);
        job.setAttempt(0);
        job.setVersion(1L);
        createdJobIds.add(job.getJobId());
        return job;
    }
}
