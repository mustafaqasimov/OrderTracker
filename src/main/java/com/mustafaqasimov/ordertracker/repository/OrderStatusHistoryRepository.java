package com.mustafaqasimov.ordertracker.repository;

import com.mustafaqasimov.ordertracker.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrder_IdOrderByCreatedAtAsc(Long orderId);

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}

