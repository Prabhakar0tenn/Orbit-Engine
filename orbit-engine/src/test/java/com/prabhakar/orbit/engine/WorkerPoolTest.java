package com.prabhakar.orbit.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.prabhakar.orbit.config.OrbitProperties;

class WorkerPoolTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private WorkerPool pool;

    @BeforeEach
    void setUp() {
        pool = new WorkerPool(new OrbitProperties(
                new OrbitProperties.Poller(1, 1000),
                new OrbitProperties.Workers(2, 1),
                new OrbitProperties.Lease(30_000)));
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        pool.shutdown();
    }

    @Test
    void runsTasksOnNamedWorkerThreads() throws InterruptedException {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        pool.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            done.countDown();
        });

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("orbit-worker-"));
    }

    @Test
    void freeSlotsShrinkAsTasksAreRunningAndNeverGoNegative() throws InterruptedException {
        assertEquals(2, pool.freeSlots());

        CountDownLatch started = new CountDownLatch(1);
        pool.execute(() -> {
            started.countDown();
            awaitRelease();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertEquals(1, pool.freeSlots());

        CountDownLatch secondStarted = new CountDownLatch(1);
        pool.execute(() -> {
            secondStarted.countDown();
            awaitRelease();
        });
        assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
        assertEquals(0, pool.freeSlots());

        pool.execute(this::awaitRelease);
        assertEquals(0, pool.freeSlots());
    }

    @Test
    void rejectsWorkWhenEveryThreadIsBusyAndTheQueueIsFull() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            pool.execute(() -> {
                started.countDown();
                awaitRelease();
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        pool.execute(this::awaitRelease);

        assertThrows(RejectedExecutionException.class, () -> pool.execute(this::awaitRelease));
    }

    private void awaitRelease() {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
