package com.prabhakar.orbit.engine;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.prabhakar.orbit.config.OrbitProperties;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.ReadyShard;
import com.prabhakar.orbit.repo.JobRepository;

@Component
@ConditionalOnProperty(name = "orbit.poller.enabled", havingValue = "true", matchIfMissing = true)
public class JobPoller {

    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);

    private final JobRepository repository;
    private final WorkerPool workerPool;
    private final JobRunner jobRunner;
    private final WorkerIdentity identity;
    private final Clock clock;
    private final OrbitProperties properties;

    public JobPoller(JobRepository repository, WorkerPool workerPool, JobRunner jobRunner,
            WorkerIdentity identity, Clock clock, OrbitProperties properties) {
        this.repository = repository;
        this.workerPool = workerPool;
        this.jobRunner = jobRunner;
        this.identity = identity;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${orbit.poller.interval-ms}")
    public void poll() {
        try {
            pollOnce();
        } catch (RuntimeException e) {
            log.error("Polling failed, retrying on the next cycle", e);
        }
    }

    void pollOnce() {
        int free = workerPool.freeSlots();
        if (free <= 0) {
            return;
        }

        long now = clock.millis();
        List<Job> candidates = new ArrayList<>();
        for (int shard = 0; shard < properties.poller().shards() && candidates.size() < free; shard++) {
            candidates.addAll(repository.findReady(ReadyShard.name(shard), now, free - candidates.size()));
        }

        for (Job candidate : candidates) {
            repository.claim(candidate, identity.id(), now, properties.lease().ms())  
                    .ifPresent(this::dispatch); // can write it like .ifPresent(job -> this.dispatch(job));
        }
    }

    private void dispatch(Job claimed) {
        try {
            workerPool.execute(() -> jobRunner.run(claimed));
        } catch (RejectedExecutionException e) {
            log.error("Worker pool rejected jobId={}; its lease will expire and the job will be recovered",
                    claimed.getJobId(), e);
        }
    }
}
