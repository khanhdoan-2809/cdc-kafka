package com.example.transport.repository;

import com.example.transport.domain.transport.Transport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransportRepository
        extends JpaRepository<Transport, Long> {

    Optional<Transport> findByIdAndDeletedAtIsNull(Long id);

    List<Transport> findAllByDeletedAtIsNull();

    boolean existsByIdAndDeletedAtIsNull(Long id);

    boolean existsByReferenceAndDeletedAtIsNull(
            String reference
    );
}