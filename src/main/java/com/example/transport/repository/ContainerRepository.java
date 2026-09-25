package com.example.transport.repository;

import com.example.transport.domain.container.ShippingContainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContainerRepository extends JpaRepository<ShippingContainer, Long> {

    List<ShippingContainer> findAllByJobId(Long jobId);

    boolean existsByContainerNumber(String containerNumber);
}