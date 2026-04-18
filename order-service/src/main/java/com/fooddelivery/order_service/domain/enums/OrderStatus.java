package com.fooddelivery.order_service.domain.enums;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PENDING, CONFIRMED, PREPARING,
    OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

    // All valid forward (and cancel) transitions in one place
    private static final Map<OrderStatus, Set<OrderStatus>> VALID =
        Map.of(
            PENDING,          Set.of(CONFIRMED, CANCELLED),
            CONFIRMED,        Set.of(PREPARING, CANCELLED),
            PREPARING,        Set.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, Set.of(DELIVERED),
            DELIVERED,        Set.of(),        // terminal
            CANCELLED,        Set.of()         // terminal
        );

    public boolean canTransitionTo(OrderStatus next) {
        return VALID.getOrDefault(this, Set.of()).contains(next);
    }
}