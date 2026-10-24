package com.prabhakar.orbit.handler;

public interface JobHandler {

    String type();

    void handle(JobContext context) throws Exception;
}
