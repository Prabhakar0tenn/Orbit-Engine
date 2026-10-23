package com.prabhakar.orbit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.prabhakar.orbit.api.dto.SubmitJobRequest;
import com.prabhakar.orbit.domain.Job;
import com.prabhakar.orbit.domain.JobStatus;
import com.prabhakar.orbit.service.InvalidJobRequestException;
import com.prabhakar.orbit.service.JobNotFoundException;
import com.prabhakar.orbit.service.JobService;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @Test
    void submitReturns201WithLocationAndBody() throws Exception {
        when(jobService.submit(any(SubmitJobRequest.class))).thenReturn(job("job-1"));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "report", "type": "SLEEP", "payload": {"ms": 200}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/jobs/job-1"))
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void submitRejectsInvalidBodyWith400AndFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "type": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.length()").value(2));

        verifyNoInteractions(jobService);
    }

    @Test
    void submitRejectsMalformedJsonWith400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void submitReturns400WhenServiceRejectsTheJobType() throws Exception {
        when(jobService.submit(any(SubmitJobRequest.class)))
                .thenThrow(new InvalidJobRequestException("Unsupported job type: NOPE"));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "bad", "type": "NOPE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported job type: NOPE"));
    }

    @Test
    void getReturns200ForExistingJob() throws Exception {
        when(jobService.get("job-1")).thenReturn(job("job-1"));

        mockMvc.perform(get("/api/v1/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.name").value("report"));
    }

    @Test
    void getReturns404ForUnknownJob() throws Exception {
        when(jobService.get("missing")).thenThrow(new JobNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/jobs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job not found: missing"));
    }

    private static Job job(String jobId) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setJobName("report");
        job.setJobType("SLEEP");
        job.setStatus(JobStatus.PENDING);
        return job;
    }
}
