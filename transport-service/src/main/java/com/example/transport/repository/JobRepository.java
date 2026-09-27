package com.example.transport.repository;

import com.example.transport.domain.job.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository
        extends JpaRepository<Job, Long> {

    Optional<Job> findByIdAndDeletedAtIsNull(Long id);

    List<Job> findAllByTransportIdAndDeletedAtIsNull(
            Long transportId
    );

    boolean existsByIdAndDeletedAtIsNull(Long id);

    boolean existsByTransportIdAndReferenceAndDeletedAtIsNull(
            Long transportId,
            String reference
    );
}