package com.example.transport.web.dto;

import com.example.transport.domain.container.ContainerStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeContainerStatusRequest(
        @NotNull ContainerStatus status
) {
}