package com.prabhakar.orbit.engine;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prabhakar.orbit.config.OrbitProperties;

import jakarta.annotation.PreDestroy;

@Component
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);
    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    private final int size;
    private final ThreadPoolExecutor executor;

    public WorkerPool(OrbitProperties properties) {
        OrbitProperties.Workers workers = properties.workers();
        this.size = workers.size();
        AtomicInteger threadNumber = new AtomicInteger();
        // core == max: the executor only adds threads beyond core once the queue is full, so a fixed size keeps all workers busy.
        this.executor = new ThreadPoolExecutor(
                size, size,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workers.queue()),
                runnable -> new Thread(runnable, "orbit-worker-" + threadNumber.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Throws RejectedExecutionException when every thread is busy and the queue is full. */
    public void execute(Runnable task) throws RejectedExecutionException {
        executor.execute(task);
    }

    public int freeSlots() {
        return Math.max(0, size - executor.getActiveCount() - executor.getQueue().size());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Workers still busy after {}s, forcing shutdown", SHUTDOWN_WAIT_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
