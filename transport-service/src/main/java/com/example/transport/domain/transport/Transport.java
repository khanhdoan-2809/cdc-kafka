package com.example.transport.domain.transport;

import com.example.transport.domain.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(name = "transport")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transport extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reference;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportStatus status;

    public Transport(String reference) {
        this.reference = reference;
        this.status = TransportStatus.CREATED;
    }

    public Transport(String reference, String description) {
        this.reference = reference;
        this.description = description;
        this.status = TransportStatus.CREATED;
    }

    public void changeStatus(TransportStatus status) {
        if (this.status == status) {
            return;
        }

        this.status = status;
    }

    public void changeDescription(String description) {
        if (Objects.equals(this.description, description)) {
            return;
        }

        this.description = description;
    }

    public void delete(Instant now) {
        markDeleted(now);
    }
}