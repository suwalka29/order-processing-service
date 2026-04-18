package com.fooddelivery.order_service.repository;

import com.fooddelivery.order_service.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}