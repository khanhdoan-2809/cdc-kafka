package com.example.transport.repository;

import com.example.transport.domain.job.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByTransportId(Long transportId);
}