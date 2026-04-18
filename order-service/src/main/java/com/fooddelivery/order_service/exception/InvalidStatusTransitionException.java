package com.fooddelivery.order_service.exception;

import com.fooddelivery.order_service.domain.enums.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}