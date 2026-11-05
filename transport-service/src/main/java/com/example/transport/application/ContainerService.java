package com.example.transport.application;

import com.example.transport.domain.container.ContainerStatus;
import com.example.transport.domain.container.ShippingContainer;
import com.example.transport.repository.ContainerRepository;
import com.example.transport.repository.JobRepository;
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
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final JobRepository jobRepository;
    private final Clock clock;
    private final RecordAccess access;

    @Transactional
    public ShippingContainer create(Long jobId, String containerNumber) {

        var job =
                jobRepository
                        .findVisibleById(jobId, access.isAdmin(), access.userId())
                        .orElseThrow(RecordNotFoundException::new);

        if (containerRepository.existsByContainerNumberAndDeletedAtIsNull(containerNumber)) {

            throw new IllegalArgumentException("Container already exists: " + containerNumber);
        }

        return containerRepository.save(new ShippingContainer(job, containerNumber));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional(readOnly = true)
    public ShippingContainer findById(Long id) {
        return containerRepository
                .findVisibleById(id, access.isAdmin(), access.userId())
                .orElseThrow(RecordNotFoundException::new);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional(readOnly = true)
    public List<ShippingContainer> findByJobId(Long jobId) {

        if (jobRepository.findVisibleById(jobId, access.isAdmin(), access.userId()).isEmpty()) {

            throw new RecordNotFoundException();
        }

        return containerRepository.findVisibleByJobId(jobId, access.isAdmin(), access.userId());
    }

    @Transactional
    public ShippingContainer changeStatus(Long id, ContainerStatus status) {

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
