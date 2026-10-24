package com.prabhakar.orbit.handler;

import org.springframework.stereotype.Component;

@Component
public class SleepHandler implements JobHandler {

    private static final long DEFAULT_SLEEP_MS = 1_000;

    @Override
    public String type() {
        return "SLEEP";
    }

    @Override
    public void handle(JobContext context) throws InterruptedException {
        Thread.sleep(context.longValue("ms", DEFAULT_SLEEP_MS));
    }
}
