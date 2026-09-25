package com.example.transport.repository;

import com.example.transport.domain.transport.Transport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportRepository extends JpaRepository<Transport, Long> {

    boolean existsByReference(String reference);
}