package com.example.transport.domain.transport;

import jakarta.persistence.*;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportAssignmentId implements Serializable {
    @Column(name = "transport_id")
    private Long transportId;

    @Column(name = "user_id")
    private UUID userId;
}
