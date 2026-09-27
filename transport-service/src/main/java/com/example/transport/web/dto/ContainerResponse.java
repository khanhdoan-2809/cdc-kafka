package com.example.transport.web.dto;

import com.example.transport.domain.container.ContainerStatus;

import java.time.Instant;

public record ContainerResponse(
        Long id,
        Long jobId,
        String containerNumber,
        ContainerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}