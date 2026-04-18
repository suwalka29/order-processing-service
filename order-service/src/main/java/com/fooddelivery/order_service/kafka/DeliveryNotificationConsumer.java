package com.fooddelivery.order_service.kafka;

import com.fooddelivery.order_service.kafka.event.OrderStatusChangedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeliveryNotificationConsumer {

    @KafkaListener(
        topics = "order-status-changed",
        groupId = "delivery-notification-group"
    )
    public void onStatusChanged(OrderStatusChangedMessage message) {
        // In production: persist to notification_records table
        // or call notification service
        log.info("[NOTIFICATION] Order {} status changed: {} -> {} for customer {}",
            message.getOrderId(),
            message.getPreviousStatus(),
            message.getNewStatus(),
            message.getCustomerId()
        );

        // Only send notification on meaningful transitions
        if (message.getNewStatus() != null) {
            switch (message.getNewStatus()) {
                case CONFIRMED -> log.info("Notify: order confirmed, preparing soon");
                case OUT_FOR_DELIVERY -> log.info("Notify: your order is on the way!");
                case DELIVERED -> log.info("Notify: order delivered!");
                case CANCELLED -> log.info("Notify: order has been cancelled");
                default -> {} // no notification for intermediate states
            }
        }
    }
}