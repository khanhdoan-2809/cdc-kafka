package com.example.transport.application;

import com.example.transport.domain.job.Job;
import com.example.transport.domain.job.JobStatus;
import com.example.transport.repository.JobRepository;
import com.example.transport.repository.TransportRepository;
import com.example.transport.security.RecordAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final TransportRepository transportRepository;
    private final Clock clock;
    private final RecordAccess access;

    @Transactional
    public Job create(Long transportId, String reference) {

        var transport =
                transportRepository
                        .findVisibleById(transportId, access.isAdmin(), access.userId())
                        .orElseThrow(RecordNotFoundException::new);

        if (jobRepository.existsByTransportIdAndReferenceAndDeletedAtIsNull(
                transportId, reference)) {

            throw new IllegalArgumentException("Job reference already exists: " + reference);
        }

        return jobRepository.save(new Job(transport, reference));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional(readOnly = true)
    public Job findById(Long id) {
        return jobRepository
                .findVisibleById(id, access.isAdmin(), access.userId())
                .orElseThrow(RecordNotFoundException::new);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional(readOnly = true)
    public List<Job> findByTransportId(Long transportId) {

        if (transportRepository
                .findVisibleById(transportId, access.isAdmin(), access.userId())
                .isEmpty()) {

            throw new RecordNotFoundException();
        }

        return jobRepository.findVisibleByTransportId(
                transportId, access.isAdmin(), access.userId());
    }

    @Transactional
    public Job changeStatus(Long id, JobStatus status) {

        var job = findById(id);

        job.changeStatus(status);

        return jobRepository.save(job);
    }

    @Transactional
    public void delete(Long id) {
        var job = findById(id);

        job.delete(Instant.now(clock));

        jobRepository.save(job);
    }
}
