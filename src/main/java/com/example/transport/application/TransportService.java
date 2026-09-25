package com.example.transport.application;

import com.example.transport.domain.transport.Transport;
import com.example.transport.domain.transport.TransportStatus;
import com.example.transport.repository.TransportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransportService {

    private final TransportRepository transportRepository;

    @Transactional
    public Transport create(String reference) {
        if (transportRepository.existsByReference(reference)) {
            throw new IllegalArgumentException("Transport reference already exists");
        }

        var transport = new Transport(reference);

        return transportRepository.save(transport);
    }

    @Transactional(readOnly = true)
    public Transport findById(Long id) {
        return transportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transport not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Transport> findAll() {
        return transportRepository.findAll();
    }

    @Transactional
    public Transport changeStatus(Long id, TransportStatus status) {
        var transport = findById(id);

        transport.changeStatus(status);

        return transport;
    }
}