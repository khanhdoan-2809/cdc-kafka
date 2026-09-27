package com.example.transport.web.dto;

import com.example.transport.domain.job.JobStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeJobStatusRequest(
        @NotNull JobStatus status
) {
}