package com.example.transport.web.dto;

import com.example.transport.domain.transport.TransportStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeTransportStatusRequest(
        @NotNull TransportStatus status
) {
}