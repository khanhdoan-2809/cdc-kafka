package com.example.transport.application;

import com.example.transport.domain.transport.Transport;
import com.example.transport.domain.transport.TransportStatus;
import com.example.transport.repository.TransportRepository;
import com.example.transport.security.RecordAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TransportService {

    private final TransportRepository transportRepository;
    private final Clock clock;
    private final RecordAccess access;

    @Transactional
    public Transport create(String reference) {
        if (transportRepository.existsByReferenceAndDeletedAtIsNull(reference)) {

            throw new IllegalArgumentException("Transport reference already exists: " + reference);
        }

        return transportRepository.save(new Transport(reference));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional(readOnly = true)
    public Transport findById(Long id) {
        return transportRepository
                .findVisibleById(id, access.isAdmin(), access.userId())
                .orElseThrow(RecordNotFoundException::new);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional(readOnly = true)
    public List<Transport> findAll() {
        return transportRepository.findAllVisible(access.isAdmin(), access.userId());
    }

    @Transactional
    public Transport changeStatus(Long id, TransportStatus status) {

        var transport = findById(id);

        transport.changeStatus(status);

        return transportRepository.save(transport);
    }

    @Transactional
    public void delete(Long id) {
        var transport = findById(id);
        transport.delete(Instant.now(clock));
        transportRepository.save(transport);
    }
}
