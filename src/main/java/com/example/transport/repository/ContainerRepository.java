package com.example.transport.repository;

import com.example.transport.domain.container.ShippingContainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContainerRepository
        extends JpaRepository<ShippingContainer, Long> {

    Optional<ShippingContainer>
    findByIdAndDeletedAtIsNull(Long id);

    List<ShippingContainer>
    findAllByJobIdAndDeletedAtIsNull(Long jobId);

    boolean existsByContainerNumberAndDeletedAtIsNull(
            String containerNumber
    );
}