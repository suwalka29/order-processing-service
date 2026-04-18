package com.fooddelivery.order_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "created_at")
    private Instant createdAt;
}