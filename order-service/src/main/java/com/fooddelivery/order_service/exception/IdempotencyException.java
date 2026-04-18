package com.fooddelivery.order_service.exception;


public class IdempotencyException extends RuntimeException {

    public IdempotencyException(String message, Throwable cause) {
        super(message, cause);
    }
}