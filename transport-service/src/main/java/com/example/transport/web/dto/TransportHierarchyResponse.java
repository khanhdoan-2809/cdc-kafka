package com.example.transport.web.dto;

import java.util.List;

public record TransportHierarchyResponse(
        Long id,
        String reference,
        List<JobResult> jobs
) {
    public record JobResult(
            Long id,
            String reference,
            List<ContainerResult> containers
    ) {
    }

    public record ContainerResult(
            Long id,
            String containerNumber
    ) {
    }
}