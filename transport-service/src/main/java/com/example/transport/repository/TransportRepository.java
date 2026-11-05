package com.example.transport.repository;

import com.example.transport.domain.transport.Transport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportRepository extends JpaRepository<Transport, Long> {

    Optional<Transport> findByIdAndDeletedAtIsNull(Long id);

    List<Transport> findAllByDeletedAtIsNull();

    boolean existsByIdAndDeletedAtIsNull(Long id);

    boolean existsByReferenceAndDeletedAtIsNull(String reference);

    @Query(
            """
            select t from Transport t
             where t.id = :id and t.deletedAt is null
               and (:admin = true or exists (select a.id from TransportAssignment a
                   where a.transport = t and a.user.id = :userId))
            """)
    Optional<Transport> findVisibleById(Long id, boolean admin, UUID userId);

    @Query(
            """
            select t from Transport t
             where t.deletedAt is null
               and (:admin = true or exists (select a.id from TransportAssignment a
                   where a.transport = t and a.user.id = :userId))
            """)
    List<Transport> findAllVisible(boolean admin, UUID userId);
}
