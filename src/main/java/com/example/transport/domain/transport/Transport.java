package com.example.transport.domain.transport;

import com.example.transport.domain.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportStatus status;

    public Transport(String reference) {
        this.reference = reference;
        this.status = TransportStatus.CREATED;
    }

    public void changeStatus(TransportStatus status) {
        if (this.status == status) {
            return;
        }

        this.status = status;
    }

    public void delete(Instant now) {
        markDeleted(now);
    }
}