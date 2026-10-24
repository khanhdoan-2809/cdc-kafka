package com.example.transport.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateTransportHierarchyRequest(
        @NotBlank String reference,
        @NotEmpty List<@NotNull @Valid JobInput> jobs
) {
    public record JobInput(
            @NotBlank String reference,
            @NotEmpty List<@NotNull @Valid CreateContainerRequest> containers
    ) {
    }
}