package com.example.transport.application;

import com.example.transport.domain.container.ContainerStatus;
import com.example.transport.domain.container.ShippingContainer;
import com.example.transport.repository.ContainerRepository;
import com.example.transport.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final JobRepository jobRepository;
    private final Clock clock;

    @Transactional
    public ShippingContainer create(
            Long jobId,
            String containerNumber) {

        var job = jobRepository
                .findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Job not found: " + jobId
                        )
                );

        if (containerRepository
                .existsByContainerNumberAndDeletedAtIsNull(
                        containerNumber
                )) {

            throw new IllegalArgumentException(
                    "Container already exists: " +
                            containerNumber
            );
        }

        return containerRepository.save(
                new ShippingContainer(
                        job,
                        containerNumber
                )
        );
    }

    @Transactional(readOnly = true)
    public ShippingContainer findById(Long id) {
        return containerRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Container not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<ShippingContainer> findByJobId(
            Long jobId) {

        if (!jobRepository
                .existsByIdAndDeletedAtIsNull(jobId)) {

            throw new IllegalArgumentException(
                    "Job not found: " + jobId
            );
        }

        return containerRepository
                .findAllByJobIdAndDeletedAtIsNull(jobId);
    }

    @Transactional
    public ShippingContainer changeStatus(
            Long id,
            ContainerStatus status) {

        var container = findById(id);

        container.changeStatus(status);

        return containerRepository.save(container);
    }

    @Transactional
    public void delete(Long id) {
        var container = findById(id);

        container.delete(Instant.now(clock));

        containerRepository.save(container);
    }
}