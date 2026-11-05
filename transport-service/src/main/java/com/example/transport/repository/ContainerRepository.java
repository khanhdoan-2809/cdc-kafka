package com.example.transport.repository;

import com.example.transport.domain.container.ShippingContainer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerRepository extends JpaRepository<ShippingContainer, Long> {

    Optional<ShippingContainer> findByIdAndDeletedAtIsNull(Long id);

    List<ShippingContainer> findAllByJobIdAndDeletedAtIsNull(Long jobId);

    boolean existsByContainerNumberAndDeletedAtIsNull(String containerNumber);

    @Query(
            """
            select c from ShippingContainer c join fetch c.job j join fetch j.transport t
             where c.id = :id and c.deletedAt is null and j.deletedAt is null and t.deletedAt is null
               and (:admin = true or exists (select a.id from TransportAssignment a
                   where a.transport = t and a.user.id = :userId))
            """)
    Optional<ShippingContainer> findVisibleById(Long id, boolean admin, UUID userId);

    @Query(
            """
            select c from ShippingContainer c join fetch c.job j join fetch j.transport t
             where j.id = :jobId and c.deletedAt is null and j.deletedAt is null and t.deletedAt is null
               and (:admin = true or exists (select a.id from TransportAssignment a
                   where a.transport = t and a.user.id = :userId))
            """)
    List<ShippingContainer> findVisibleByJobId(Long jobId, boolean admin, UUID userId);
}
