package com.example.transport.application;

import com.example.transport.domain.container.ShippingContainer;
import com.example.transport.domain.job.Job;
import com.example.transport.domain.transport.Transport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransportHierarchyService {

    private final TransportService transportService;
    private final JobService jobService;
    private final ContainerService containerService;

    @Transactional
    public CreatedTransport create(String reference, List<NewJob> jobs) {
        var transport = transportService.create(reference);
        var createdJobs = new ArrayList<CreatedJob>();

        for (var jobRequest : jobs) {
            var job = jobService.create(transport.getId(), jobRequest.reference());
            var containers = new ArrayList<ShippingContainer>();

            for (var containerNumber : jobRequest.containerNumbers()) {
                var container = containerService.create(job.getId(), containerNumber);
                containers.add(container);
            }

            createdJobs.add(new CreatedJob(job, List.copyOf(containers)));
        }

        return new CreatedTransport(transport, List.copyOf(createdJobs));
    }

    public record NewJob(String reference, List<String> containerNumbers) {
    }

    public record CreatedTransport(Transport transport, List<CreatedJob> jobs) {
    }

    public record CreatedJob(Job job, List<ShippingContainer> containers) {
    }
}