package com.example.transport.web;

import com.example.transport.application.JobService;
import com.example.transport.domain.job.Job;
import com.example.transport.web.dto.ChangeJobStatusRequest;
import com.example.transport.web.dto.CreateJobRequest;
import com.example.transport.web.dto.JobResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping("/api/transports/{transportId}/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@PathVariable Long transportId, @Valid @RequestBody CreateJobRequest request) {
        return toResponse(jobService.create(transportId, request.reference()));
    }

    @GetMapping("/api/transports/{transportId}/jobs")
    public List<JobResponse> getByTransport(@PathVariable Long transportId) {
        return jobService.findByTransportId(transportId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/api/jobs/{id}")
    public JobResponse get(@PathVariable Long id) {
        return toResponse(jobService.findById(id));
    }

    @PatchMapping("/api/jobs/{id}/status")
    public JobResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeJobStatusRequest request) {
        return toResponse(jobService.changeStatus(id, request.status()));
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTransport().getId(),
                job.getReference(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}