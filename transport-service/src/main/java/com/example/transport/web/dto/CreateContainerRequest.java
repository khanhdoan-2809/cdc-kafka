package com.example.transport.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateContainerRequest(
        @NotBlank String containerNumber
) {
}