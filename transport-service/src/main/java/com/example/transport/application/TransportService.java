package com.example.transport.application;

import com.example.transport.domain.transport.Transport;
import com.example.transport.domain.transport.TransportStatus;
import com.example.transport.repository.TransportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransportService {

    private final TransportRepository transportRepository;
    private final Clock clock;

    @Transactional
    public Transport create(String reference) {
        if (transportRepository
                .existsByReferenceAndDeletedAtIsNull(reference)) {

            throw new IllegalArgumentException(
                    "Transport reference already exists: " +
                            reference
            );
        }

        return transportRepository.save(
                new Transport(reference)
        );
    }

    @Transactional(readOnly = true)
    public Transport findById(Long id) {
        return transportRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transport not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<Transport> findAll() {
        return transportRepository
                .findAllByDeletedAtIsNull();
    }

    @Transactional
    public Transport changeStatus(
            Long id,
            TransportStatus status) {

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