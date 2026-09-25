package com.example.transport.web.dto;

import com.example.transport.domain.job.JobStatus;

import java.time.Instant;

public record JobResponse(
        Long id,
        Long transportId,
        String reference,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}