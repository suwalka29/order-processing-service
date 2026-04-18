package com.fooddelivery.order_service.repository;

import com.fooddelivery.order_service.domain.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, String> {
}