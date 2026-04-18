package com.fooddelivery.order_service.kafka.event;

import com.fooddelivery.order_service.domain.enums.OrderStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

// This is a Spring ApplicationEvent (not the Kafka payload).
// We publish this inside the DB transaction.
// @TransactionalEventListener fires it AFTER commit.
@Getter
@RequiredArgsConstructor
public class OrderStatusChangedEvent {
    private final UUID orderId;
    private final UUID customerId;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
}