package com.fooddelivery.order_service.dto.request;

import com.fooddelivery.order_service.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "New status is required")
    private OrderStatus newStatus;
}