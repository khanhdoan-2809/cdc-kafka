package com.example.transport.web.dto;

import com.example.transport.domain.transport.TransportStatus;

import java.time.Instant;

public record TransportResponse(
        Long id,
        String reference,
        TransportStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}