package com.prabhakar.orbit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(String region, DynamoDb dynamodb) {

    public record DynamoDb(String endpoint, String jobsTable, String executionsTable) {
    }
}

