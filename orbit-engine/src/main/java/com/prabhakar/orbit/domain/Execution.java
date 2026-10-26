package com.prabhakar.orbit.domain;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
public class Execution {
    private String jobId;
    private String sk;
    private Integer attempt;
    private ExecutionStatus status;
    private String workerId;
    private Long startedAt;
    private Long finishedAt;
    private Long durationMs;
    private String errorMessage;

    // Zero-padded so that sorting by text equals sorting by time.
    public static String sortKey(long startedAt, int attempt) {
        return "EXEC#%013d#%d".formatted(startedAt, attempt);
    }

    @DynamoDbPartitionKey
    public String getJobId() {
        return jobId;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }
}
