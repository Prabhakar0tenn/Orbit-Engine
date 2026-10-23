package com.prabhakar.orbit.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prabhakar.orbit.api.dto.JobResponse;
import com.prabhakar.orbit.api.dto.SubmitJobRequest;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.service.JobService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> submit(@Valid @RequestBody SubmitJobRequest request) {
        Job job = jobService.submit(request);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + job.getJobId()))
                .body(JobResponse.from(job));
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable String jobId) {
        return JobResponse.from(jobService.get(jobId));
    }
}
