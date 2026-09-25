package com.example.transport.web;

import com.example.transport.application.TransportService;
import com.example.transport.domain.transport.Transport;
import com.example.transport.web.dto.ChangeTransportStatusRequest;
import com.example.transport.web.dto.CreateTransportRequest;
import com.example.transport.web.dto.TransportResponse;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransportResponse create(@Valid @RequestBody CreateTransportRequest request) {
        return toResponse(transportService.create(request.reference()));
    }

    @GetMapping("/{id}")
    public TransportResponse get(@PathVariable Long id) {
        return toResponse(transportService.findById(id));
    }

    @GetMapping
    public List<TransportResponse> getAll() {
        return transportService.findAll().stream().map(this::toResponse).toList();
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