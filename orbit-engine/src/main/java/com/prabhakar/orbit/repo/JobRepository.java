package com.prabhakar.orbit.repo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.prabhakar.orbit.config.AwsProperties;
import com.prabhakar.orbit.domain.Job;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class JobRepository {

    private static final String READY_INDEX = "ready-index";

    private final DynamoDbClient client;
    private final DynamoDbTable<Job> table;
    private final DynamoDbIndex<Job> readyIndex;
    private final String tableName;

    public JobRepository(DynamoDbClient client, DynamoDbEnhancedClient enhancedClient, AwsProperties properties) {
        this.client = client;
        this.tableName = properties.dynamodb().jobsTable();
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Job.class));
        this.readyIndex = table.index(READY_INDEX);
    }

    public void save(Job job) {
        table.putItem(job);
    }

    public Optional<Job> findById(String jobId) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(jobId).build()));
    }

    // The GSI is eventually consistent: results are only candidates, claim() decides.
    public List<Job> findReady(String shard, long now, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortLessThanOrEqualTo(
                        Key.builder().partitionValue(shard).sortValue(now).build()))
                .build();
        return readyIndex.query(request).stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .toList();
    }

    /** Returns true if this worker won the claim, false if another worker got there first. */
    public boolean claim(Job job, String workerId, long now, long leaseMs) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("jobId", AttributeValue.fromS(job.getJobId())))
                .updateExpression("SET #st = :running, leaseOwner = :owner, leaseExpiresAt = :lease, "
                        + "runningMarker = :marker, updatedAt = :now, #ver = #ver + :one, "
                        + "#att = if_not_exists(#att, :zero) + :one REMOVE readyShard")
                .conditionExpression("#st IN (:pending, :retrying) AND nextRunAt <= :now AND #ver = :expectedVersion")
                .expressionAttributeNames(Map.of("#st", "status", "#ver", "version", "#att", "attempt"))
                .expressionAttributeValues(Map.ofEntries(
                        Map.entry(":running", AttributeValue.fromS("RUNNING")),
                        Map.entry(":pending", AttributeValue.fromS("PENDING")),
                        Map.entry(":retrying", AttributeValue.fromS("RETRYING")),
                        Map.entry(":owner", AttributeValue.fromS(workerId)),
                        Map.entry(":lease", number(now + leaseMs)),
                        Map.entry(":marker", AttributeValue.fromS("RUNNING")),
                        Map.entry(":now", number(now)),
                        Map.entry(":one", number(1)),
                        Map.entry(":zero", number(0)),
                        Map.entry(":expectedVersion", number(job.getVersion()))))
                .build();
        try {
            client.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private static AttributeValue number(long value) {
        return AttributeValue.fromN(Long.toString(value));
    }
}
