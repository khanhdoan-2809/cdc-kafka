package com.example.transport.domain.container;

import com.example.transport.domain.job.Job;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "container")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable = false, unique = true)
    private String containerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ShippingContainer(Job job, String containerNumber) {
        this.job = job;
        this.containerNumber = containerNumber;
        this.status = ContainerStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(ContainerStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}