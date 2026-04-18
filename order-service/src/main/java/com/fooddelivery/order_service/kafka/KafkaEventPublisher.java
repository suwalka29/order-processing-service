package com.fooddelivery.order_service.kafka;

import com.fooddelivery.order_service.config.KafkaConfig;
import com.fooddelivery.order_service.kafka.event.OrderStatusChangedEvent;
import com.fooddelivery.order_service.kafka.event.OrderStatusChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, OrderStatusChangedMessage> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStatusChanged(OrderStatusChangedEvent event) {

        OrderStatusChangedMessage message = OrderStatusChangedMessage.builder()
                .eventId(UUID.randomUUID())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .previousStatus(event.getPreviousStatus())
                .newStatus(event.getNewStatus())
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(
                KafkaConfig.ORDER_STATUS_TOPIC,
                event.getOrderId().toString(),
                message
        );
    }
}