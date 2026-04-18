package com.fooddelivery.order_service.controller;

import com.fooddelivery.order_service.domain.enums.OrderStatus;
import com.fooddelivery.order_service.dto.request.*;
import com.fooddelivery.order_service.dto.response.OrderResponse;
import com.fooddelivery.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // POST /api/v1/orders
    // Requires Idempotency-Key and X-Customer-Id headers
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Customer-Id") UUID customerId,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse response = orderService.createOrder(
            idempotencyKey, customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // PATCH /api/v1/orders/{orderId}/status
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable UUID orderId,
            @RequestHeader("X-Customer-Id") UUID customerId,
            @Valid @RequestBody UpdateStatusRequest request) {

        return ResponseEntity.ok(
            orderService.updateStatus(orderId, customerId, request));
    }

    // GET /api/v1/orders/{orderId}
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-Customer-Id") UUID customerId) {

        return ResponseEntity.ok(orderService.getOrder(orderId, customerId));
    }

    // GET /api/v1/orders?customerId=...&status=...&page=0&size=20
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page, 
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
            orderService.listOrders(customerId, status, page, size));
    }
}