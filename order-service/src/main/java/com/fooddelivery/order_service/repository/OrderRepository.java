package com.fooddelivery.order_service.repository;

import com.fooddelivery.order_service.domain.entity.Order;
import com.fooddelivery.order_service.domain.enums.OrderStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(UUID customerId,
                                          OrderStatus status,
                                          Pageable pageable);
}