package com.example.transport.application;

import com.example.transport.domain.job.Job;
import com.example.transport.domain.job.JobStatus;
import com.example.transport.repository.JobRepository;
import com.example.transport.repository.TransportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final TransportRepository transportRepository;
    private final Clock clock;

    @Transactional
    public Job create(
            Long transportId,
            String reference) {

        var transport = transportRepository
                .findByIdAndDeletedAtIsNull(transportId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transport not found: " +
                                        transportId
                        )
                );

        if (jobRepository
                .existsByTransportIdAndReferenceAndDeletedAtIsNull(
                        transportId,
                        reference
                )) {

            throw new IllegalArgumentException(
                    "Job reference already exists: " +
                            reference
            );
        }

        return jobRepository.save(
                new Job(transport, reference)
        );
    }

    @Transactional(readOnly = true)
    public Job findById(Long id) {
        return jobRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Job not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<Job> findByTransportId(
            Long transportId) {

        if (!transportRepository
                .existsByIdAndDeletedAtIsNull(transportId)) {

            throw new IllegalArgumentException(
                    "Transport not found: " +
                            transportId
            );
        }

        return jobRepository
                .findAllByTransportIdAndDeletedAtIsNull(
                        transportId
                );
    }

    @Transactional
    public Job changeStatus(
            Long id,
            JobStatus status) {

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