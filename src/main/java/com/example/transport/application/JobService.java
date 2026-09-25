package com.example.transport.application;

import com.example.transport.domain.job.Job;
import com.example.transport.domain.job.JobStatus;
import com.example.transport.repository.JobRepository;
import com.example.transport.repository.TransportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final TransportRepository transportRepository;

    @Transactional
    public Job create(Long transportId, String reference) {
        var transport = transportRepository.findById(transportId)
                .orElseThrow(() -> new IllegalArgumentException("Transport not found: " + transportId));

        var job = new Job(transport, reference);

        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Job findById(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Job> findByTransportId(Long transportId) {
        return jobRepository.findAllByTransportId(transportId);
    }

    @Transactional
    public Job changeStatus(Long id, JobStatus status) {
        var job = findById(id);

        job.changeStatus(status);

        return job;
    }
}