package com.fooddelivery.order_service.kafka.event;

import com.fooddelivery.order_service.domain.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data 
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusChangedMessage {
    private UUID eventId;          // unique per event, for deduplication
    private UUID orderId;
    private UUID customerId;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private Instant timestamp;
}