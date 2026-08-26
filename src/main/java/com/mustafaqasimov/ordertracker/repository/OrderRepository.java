package com.mustafaqasimov.ordertracker.repository;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "user"})
    Optional<Order> findWithItemsById(Long id);

    Page<Order> findByUser_Id(Long userId, Pageable pageable);

    Page<Order> findByUser_IdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByPaymentReference(String paymentReference);

    Optional<Order> findByShipmentTrackingNumber(String shipmentTrackingNumber);

    boolean existsByOrderNumber(String orderNumber);

    long countByStatus(OrderStatus status);
}
