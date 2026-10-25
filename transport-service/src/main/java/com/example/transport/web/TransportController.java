package com.example.transport.web;

import com.example.transport.application.TransportHierarchyService;
import com.example.transport.application.TransportService;
import com.example.transport.domain.transport.Transport;
import com.example.transport.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transports")
@RequiredArgsConstructor
public class TransportController {

    private final TransportService transportService;
    private final TransportHierarchyService transportHierarchyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransportResponse create(@Valid @RequestBody CreateTransportRequest request) {
        return toResponse(transportService.create(request.reference()));
    }

    @PostMapping("/with-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public TransportHierarchyResponse createHierarchy(@Valid @RequestBody CreateTransportHierarchyRequest request) {
        var jobs = request.jobs().stream()
                .map(job -> new TransportHierarchyService.NewJob(
                        job.reference(),
                        job.containers().stream().map(CreateContainerRequest::containerNumber).toList()
                ))
                .toList();

        var result = transportHierarchyService.create(request.reference(), jobs);

        return new TransportHierarchyResponse(
                result.transport().getId(),
                result.transport().getReference(),
                result.jobs().stream()
                        .map(createdJob -> new TransportHierarchyResponse.JobResult(
                                createdJob.job().getId(),
                                createdJob.job().getReference(),
                                createdJob.containers().stream()
                                        .map(container -> new TransportHierarchyResponse.ContainerResult(
                                                container.getId(),
                                                container.getContainerNumber()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    @PatchMapping("/{id}/status")
    public TransportResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeTransportStatusRequest request) {
        return toResponse(transportService.changeStatus(id, request.status()));
    }

    private TransportResponse toResponse(Transport transport) {
        return new TransportResponse(
                transport.getId(),
                transport.getReference(),
                transport.getStatus(),
                transport.getCreatedAt(),
                transport.getUpdatedAt()
        );
    }

}