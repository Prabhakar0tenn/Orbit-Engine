package com.prabhakar.orbit.domain;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@DynamoDbBean
@Getter
@Setter
public class Job {
    private String jobId;
    private String jobName;
    private String jobType;
    private String payload;
    private ScheduleType scheduleType;
    private String cronExpr;
    private Long fixedRateSec;
    private JobStatus status;
    private Long nextRunAt;
    private Integer attempt;
    private Integer maxAttempts;
    private Long backoffBaseMs;
    private Long backoffCapMs;
    private Integer timeoutSec;
    private String leaseOwner;
    private Long leaseExpiresAt;
    private Boolean cancelRequested;
    private Long version;
    private String readyShard;
    private String runningMarker;
    private String lastError;
    private String lastLogKey;
    private String idempotencyKey;
    private Long createdAt;
    private Long updatedAt;

    @DynamoDbPartitionKey
    //@DynamoDbAttribute("jobId") don't need as attribute name already matches our field name
    public String getJobId() {
        return jobId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "ready-index")
    public String getReadyShard() {
        return readyShard;
    }

    @DynamoDbSecondarySortKey(indexNames = "ready-index")
    public Long getNextRunAt() {
        return nextRunAt;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "running-index")
    public String getRunningMarker() {
        return runningMarker;
    }

    @DynamoDbSecondarySortKey(indexNames = "running-index")
    public Long getLeaseExpiresAt() {
        return leaseExpiresAt;
    }
}