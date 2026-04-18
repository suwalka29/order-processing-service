package com.fooddelivery.order_service.dto.response;

import com.fooddelivery.order_service.domain.entity.Order;
import com.fooddelivery.order_service.domain.entity.OrderItem;
import com.fooddelivery.order_service.domain.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class OrderResponse {

    private UUID id;
    private UUID customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<ItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponse from(Order order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setCustomerId(order.getCustomerId());
        r.setStatus(order.getStatus());
        r.setTotalAmount(order.getTotalAmount());
        r.setCreatedAt(order.getCreatedAt());
        r.setUpdatedAt(order.getUpdatedAt());
        r.setItems(order.getItems().stream()
            .map(ItemResponse::from)
            .collect(Collectors.toList()));
        return r;
    }

    @Data
    public static class ItemResponse {
        private UUID id;
        private UUID menuItemId;
        private String name;
        private int quantity;
        private BigDecimal unitPrice;

        public static ItemResponse from(OrderItem i) {
            ItemResponse r = new ItemResponse();
            r.setId(i.getId());
            r.setMenuItemId(i.getMenuItemId());
            r.setName(i.getName());
            r.setQuantity(i.getQuantity());
            r.setUnitPrice(i.getUnitPrice());
            return r;
        }
    }
}