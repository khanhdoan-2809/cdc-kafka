package com.example.transport.domain.transport;

import com.example.transport.security.Account;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(
        name = "transport_assignment",
        indexes = @Index(name = "idx_transport_assignment_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportAssignment {
    @EmbeddedId private TransportAssignmentId id;

    @MapsId("transportId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_id", nullable = false)
    private Transport transport;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Account user;

    public TransportAssignment(Transport transport, Account user) {
        this.id = new TransportAssignmentId(transport.getId(), user.getId());
        this.transport = transport;
        this.user = user;
    }
}
