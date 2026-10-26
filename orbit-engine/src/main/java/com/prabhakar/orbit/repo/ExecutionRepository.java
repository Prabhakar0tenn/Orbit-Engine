package com.prabhakar.orbit.repo;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.prabhakar.orbit.config.AwsProperties;
import com.prabhakar.orbit.domain.Execution;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class ExecutionRepository {

    private final DynamoDbTable<Execution> table;

    public ExecutionRepository(DynamoDbEnhancedClient enhancedClient, AwsProperties properties) {
        this.table = enhancedClient.table(properties.dynamodb().executionsTable(), TableSchema.fromBean(Execution.class));
    }

    /** Inserts the run, or overwrites it when the same run is saved again with its final result. */
    public void save(Execution execution) {
        table.putItem(execution);
    }

    public List<Execution> findLatest(String jobId, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(jobId).build()))
                .scanIndexForward(false)
                .build();
        return table.query(request).items().stream()
                .limit(limit)
                .toList();
    }
}
