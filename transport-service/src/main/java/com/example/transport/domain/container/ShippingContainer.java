package com.example.transport.domain.container;

import com.example.transport.domain.common.AuditableEntity;
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
public class ShippingContainer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "container_number", nullable = false)
    private String containerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerStatus status;

    public ShippingContainer(
            Job job,
            String containerNumber) {

        this.job = job;
        this.containerNumber = containerNumber;
        this.status = ContainerStatus.CREATED;
    }

    public void changeStatus(ContainerStatus status) {
        if (this.status == status) {
            return;
        }

        this.status = status;
    }

    public void delete(Instant now) {
        markDeleted(now);
    }
}