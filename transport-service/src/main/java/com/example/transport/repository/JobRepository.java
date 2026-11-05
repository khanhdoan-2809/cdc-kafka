package com.example.transport.repository;

import com.example.transport.domain.job.Job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByIdAndDeletedAtIsNull(Long id);

    List<Job> findAllByTransportIdAndDeletedAtIsNull(Long transportId);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    boolean existsByTransportIdAndReferenceAndDeletedAtIsNull(Long transportId, String reference);

    @Query(
            """
            select j from Job j join fetch j.transport t
             where j.id = :id and j.deletedAt is null and t.deletedAt is null
               and (:admin = true or exists (select a.id from TransportAssignment a
                   where a.transport = t and a.user.id = :userId))
            """)
    Optional<Job> findVisibleById(Long id, boolean admin, UUID userId);

    @Query(
            """
            select j from Job j join fetch j.transport t
             where t.id = :transportId and j.deletedAt is null and t.deletedAt is null
               and (:admin = true or exists (select a.id from TransportAssignment a
                   where a.transport = t and a.user.id = :userId))
            """)
    List<Job> findVisibleByTransportId(Long transportId, boolean admin, UUID userId);
}
