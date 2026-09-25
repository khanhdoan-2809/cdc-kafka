package com.example.transport.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
        @NotBlank String reference
) {
}