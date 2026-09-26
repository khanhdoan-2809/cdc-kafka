package com.example.transport.application;

import com.example.transport.application.context.CurrentRequestContext;
import com.example.transport.domain.transport.Transport;
import com.example.transport.domain.transport.TransportStatus;
import com.example.transport.repository.TransportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransportService {

    private final TransportRepository transportRepository;
    private final CurrentRequestContext currentRequestContext;

    @Transactional
    public Transport create(String reference) {
        var context = currentRequestContext.get();

        if (transportRepository.existsByReference(reference)) {
            throw new IllegalArgumentException(
                    "Transport reference already exists"
            );
        }

        var transport = new Transport(reference);

        transportRepository.save(transport);

        log.info(
                "Transport created. transportId={}, actorId={}",
                transport.getId(),
                context.actorId()
        );

        return transport;
    }

    @Transactional
    public Transport changeStatus(
            Long id,
            TransportStatus status) {

        var context = currentRequestContext.get();

        var transport = transportRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transport not found: " + id
                        )
                );

        var oldStatus = transport.getStatus();

        transport.changeStatus(status);

        log.info(
                "Transport status changed. transportId={}, oldStatus={}, newStatus={}, actorId={}",
                transport.getId(),
                oldStatus,
                status,
                context.actorId()
        );

        return transport;
    }

}