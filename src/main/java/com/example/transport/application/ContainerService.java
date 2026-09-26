package com.example.transport.application;

import com.example.transport.application.context.CurrentRequestContext;
import com.example.transport.domain.container.ContainerStatus;
import com.example.transport.domain.container.ShippingContainer;
import com.example.transport.repository.ContainerRepository;
import com.example.transport.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final JobRepository jobRepository;
    private final CurrentRequestContext currentRequestContext;

    @Transactional
    public ShippingContainer create(Long jobId, String containerNumber) {
        var context = currentRequestContext.get();

        if (containerRepository.existsByContainerNumber(containerNumber)) {
            throw new IllegalArgumentException(
                    "Container already exists: " + containerNumber
            );
        }

        var job = jobRepository.findById(jobId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Job not found: " + jobId)
                );

        var container = new ShippingContainer(job, containerNumber);

        containerRepository.save(container);

        log.info(
                "Container created. containerId={}, actorId={}",
                container.getId(),
                context.actorId()
        );

        return container;
    }

    @Transactional
    public ShippingContainer changeStatus(
            Long id,
            ContainerStatus status) {

        var context = currentRequestContext.get();

        var container = containerRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Container not found: " + id
                        )
                );

        var oldStatus = container.getStatus();

        container.changeStatus(status);

        log.info(
                "Container status changed. containerId={}, oldStatus={}, newStatus={}, actorId={}",
                container.getId(),
                oldStatus,
                status,
                context.actorId()
        );

        return container;
    }

    @Transactional(readOnly = true)
    public ShippingContainer findById(Long id) {
        return containerRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Container not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<ShippingContainer> findByJobId(Long jobId) {
        return containerRepository.findAllByJobId(jobId);
    }
}