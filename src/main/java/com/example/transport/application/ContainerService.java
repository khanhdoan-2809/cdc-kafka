package com.example.transport.application;

import com.example.transport.domain.container.ContainerStatus;
import com.example.transport.domain.container.ShippingContainer;
import com.example.transport.repository.ContainerRepository;
import com.example.transport.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final JobRepository jobRepository;

    @Transactional
    public ShippingContainer create(Long jobId, String containerNumber) {
        if (containerRepository.existsByContainerNumber(containerNumber)) {
            throw new IllegalArgumentException("Container already exists: " + containerNumber);
        }

        var job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        var container = new ShippingContainer(job, containerNumber);

        return containerRepository.save(container);
    }

    @Transactional(readOnly = true)
    public ShippingContainer findById(Long id) {
        return containerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ShippingContainer> findByJobId(Long jobId) {
        return containerRepository.findAllByJobId(jobId);
    }

    @Transactional
    public ShippingContainer changeStatus(Long id, ContainerStatus status) {
        var container = findById(id);

        container.changeStatus(status);

        return container;
    }
}