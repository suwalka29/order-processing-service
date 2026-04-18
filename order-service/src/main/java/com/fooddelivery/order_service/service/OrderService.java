package com.fooddelivery.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order_service.domain.entity.*;
import com.fooddelivery.order_service.domain.enums.OrderStatus;
import com.fooddelivery.order_service.dto.request.*;
import com.fooddelivery.order_service.dto.response.OrderResponse;
import com.fooddelivery.order_service.exception.*;
import com.fooddelivery.order_service.kafka.event.OrderStatusChangedEvent;
import com.fooddelivery.order_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final IdempotencyKeyRepository idempotencyRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // Idempotency: the entire create is wrapped in one transaction.
    // We check AND store the key atomically with the order insert.
    // If same key arrives twice concurrently, DB unique constraint
    // on idempotency_keys PK prevents duplicate processing.
    @Transactional
    public OrderResponse createOrder(String idempotencyKey,
                                     UUID customerId,
                                     CreateOrderRequest request) {
        // 1. Check if this idempotency key already exists
        return idempotencyRepo.findById(idempotencyKey)
            .map(existing -> {
                // Return the stored response — same status code, same body
                try {
                    log.info("Idempotency hit for key: {}", idempotencyKey);
                    return objectMapper.readValue(
                        existing.getResponseBody(), OrderResponse.class);
                } catch (Exception e) {
                    throw new IdempotencyException("Failed to deserialize cached response", e);
                }
            })
            .orElseGet(() -> {
                // 2. Build order entity
                Order order = Order.builder()
                    .customerId(customerId)
                    .status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.ZERO)
                    .build();

                // 3. Build items and calculate total
                BigDecimal total = BigDecimal.ZERO;
                for (var itemReq : request.getItems()) {
                    OrderItem item = OrderItem.builder()
                        .order(order)
                        .menuItemId(itemReq.getMenuItemId())
                        .name(itemReq.getName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .build();
                    order.getItems().add(item);
                    total = total.add(
                        itemReq.getUnitPrice()
                            .multiply(BigDecimal.valueOf(itemReq.getQuantity())));
                }
                order.setTotalAmount(total);

                // 4. Save order
                Order saved = orderRepo.save(order);
                OrderResponse response = OrderResponse.from(saved);

                // 5. Persist idempotency key WITHIN the same transaction
                try {
                    IdempotencyKey keyRecord = IdempotencyKey.builder()
                        .key(idempotencyKey)
                        .responseBody(objectMapper.writeValueAsString(response))
                        .statusCode(201)
                        .createdAt(Instant.now())
                        .build();
                    idempotencyRepo.save(keyRecord);
                } catch (Exception e) {
                    throw new IdempotencyException("Failed to store idempotency key", e);
                }

                log.info("Order created: {} for customer: {}", saved.getId(), customerId);
                return response;
            });
    }

     // Status update: idempotent + optimistic locking
    @Transactional
    public OrderResponse updateStatus(UUID orderId,
                                      UUID requestingCustomerId,
                                      UpdateStatusRequest request) {
        Order order = orderRepo.findByIdWithItems(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));


        // Authorization — owner only for ANY update
      if (!order.getCustomerId().equals(requestingCustomerId)) {
          throw new ForbiddenException();
}

        OrderStatus newStatus = request.getNewStatus();

        // IDEMPOTENCY: same transition already applied?
        // Return current state without re-publishing event.
        // This handles the "internal service retries 3 times" requirement.
        if (order.getStatus() == newStatus) {
            log.info("Idempotent status update: order {} already in status {}",
                orderId, newStatus);
            return OrderResponse.from(order);
        }

        // Authorization: only owner can cancel
        if (newStatus == OrderStatus.CANCELLED
                && !order.getCustomerId().equals(requestingCustomerId)) {
            throw new ForbiddenException();
        }

        // Validate transition
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(order.getStatus(), newStatus);
        }


        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);

        // save() triggers @Version check.
        // Concurrent update on same order → OptimisticLockException → 409
        Order saved = orderRepo.save(order);

        // Publish Spring event — Kafka listener fires AFTER this TX commits
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
            saved.getId(), saved.getCustomerId(), previousStatus, newStatus));

        log.info("Order {} transitioned {} -> {}", orderId, previousStatus, newStatus);
        return OrderResponse.from(saved);
    }

    // Retrieval — only owner can fetch
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID requestingCustomerId) {
        Order order = orderRepo.findByIdWithItems(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(requestingCustomerId)) {
            throw new ForbiddenException();
        }
        return OrderResponse.from(order);
    }

    // Paginated list
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(UUID customerId,
                                           OrderStatus status,
                                           int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
            Sort.by("createdAt").descending());

        if (customerId != null && status != null) {
            return orderRepo.findByCustomerIdAndStatus(
                customerId, status, pageable).map(OrderResponse::from);
        } else if (customerId != null) {
            return orderRepo.findByCustomerId(
                customerId, pageable).map(OrderResponse::from);
        } else if (status != null) {
            return orderRepo.findByStatus(
                status, pageable).map(OrderResponse::from);
        }
        return orderRepo.findAll(pageable).map(OrderResponse::from);
    }
}