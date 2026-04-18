package com.fooddelivery.order_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull private UUID menuItemId;
        @NotBlank private String name;
        @Min(1) private int quantity;
        @DecimalMin("0.01") private BigDecimal unitPrice;
    }
}