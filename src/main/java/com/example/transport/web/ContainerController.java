package com.example.transport.web;

import com.example.transport.application.ContainerService;
import com.example.transport.domain.container.ShippingContainer;
import com.example.transport.web.dto.ChangeContainerStatusRequest;
import com.example.transport.web.dto.ContainerResponse;
import com.example.transport.web.dto.CreateContainerRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;

    @PostMapping("/api/jobs/{jobId}/containers")
    @ResponseStatus(HttpStatus.CREATED)
    public ContainerResponse create(@PathVariable Long jobId, @Valid @RequestBody CreateContainerRequest request) {
        return toResponse(containerService.create(jobId, request.containerNumber()));
    }

    @GetMapping("/api/jobs/{jobId}/containers")
    public List<ContainerResponse> getByJob(@PathVariable Long jobId) {
        return containerService.findByJobId(jobId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/api/containers/{id}")
    public ContainerResponse get(@PathVariable Long id) {
        return toResponse(containerService.findById(id));
    }

    @PatchMapping("/api/containers/{id}/status")
    public ContainerResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeContainerStatusRequest request) {
        return toResponse(containerService.changeStatus(id, request.status()));
    }

    private ContainerResponse toResponse(ShippingContainer container) {
        return new ContainerResponse(
                container.getId(),
                container.getJob().getId(),
                container.getContainerNumber(),
                container.getStatus(),
                container.getCreatedAt(),
                container.getUpdatedAt()
        );
    }
}