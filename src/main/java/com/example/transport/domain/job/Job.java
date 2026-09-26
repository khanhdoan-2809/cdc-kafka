package com.example.transport.domain.job;

import com.example.transport.domain.common.AuditableEntity;
import com.example.transport.domain.transport.Transport;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_id", nullable = false)
    private Transport transport;

    @Column(nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    public Job(
            Transport transport,
            String reference) {

        this.transport = transport;
        this.reference = reference;
        this.status = JobStatus.CREATED;
    }

    public void changeStatus(JobStatus status) {
        if (this.status == status) {
            return;
        }

        this.status = status;
    }

    public void delete(Instant now) {
        markDeleted(now);
    }
}