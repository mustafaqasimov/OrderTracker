package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.dto.request.CreateOrderRequest;
import com.mustafaqasimov.ordertracker.dto.request.OrderItemRequest;
import com.mustafaqasimov.ordertracker.dto.request.UpdateOrderRequest;
import com.mustafaqasimov.ordertracker.dto.response.OrderResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderStatusHistoryResponse;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.OrderItem;
import com.mustafaqasimov.ordertracker.entity.OrderStatusHistory;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;
import com.mustafaqasimov.ordertracker.exception.error.InvalidOperationException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.exception.error.UnauthorizedException;
import com.mustafaqasimov.ordertracker.mapper.OrderMapper;
import com.mustafaqasimov.ordertracker.repository.OrderRepository;
import com.mustafaqasimov.ordertracker.repository.OrderStatusHistoryRepository;
import com.mustafaqasimov.ordertracker.repository.UserRepository;
import com.mustafaqasimov.ordertracker.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final EmailNotificationService emailNotificationService;

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ---------- USER ACTIONS ----------

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .customerEmail(user.getEmail())
                .status(OrderStatus.PENDING)
                .currency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "USD")
                .shippingAddress(request.getShippingAddress())
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (OrderItemRequest ir : request.getItems()) {
            order.addItem(buildItem(ir));
        }
        order.setTotalAmount(sum(order));

        Order saved = orderRepository.save(order);

        recordHistory(saved, null, saved.getStatus(), StatusChangeSource.USER, "Order created");

        log.info("Order {} created by user {}", saved.getOrderNumber(), userId);
        return orderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long orderId) {
        Order order = loadOwnedOrAdmin(orderId);
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMyOrders(OrderStatus status, Pageable pageable) {
        Long userId = SecurityUtils.currentUserId();
        Page<Order> page = (status == null)
                ? orderRepository.findByUser_Id(userId, pageable)
                : orderRepository.findByUser_IdAndStatus(userId, status, pageable);
        return page.map(orderMapper::toResponse);
    }

    @Transactional
    public OrderResponse updateOrder(Long orderId, UpdateOrderRequest request) {
        Order order = loadOwnedOrAdmin(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOperationException(
                    "Order can only be updated while in PENDING status (current: " + order.getStatus() + ")");
        }

        if (request.getShippingAddress() != null && !request.getShippingAddress().isBlank()) {
            order.setShippingAddress(request.getShippingAddress());
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            order.getItems().clear();
            for (OrderItemRequest ir : request.getItems()) {
                order.addItem(buildItem(ir));
            }
            order.setTotalAmount(sum(order));
        }

        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = loadOwnedOrAdmin(orderId);
        Set<OrderStatus> cancellable = Set.of(OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED);
        if (!cancellable.contains(order.getStatus())) {
            throw new InvalidOperationException(
                    "Cannot cancel an order in status " + order.getStatus());
        }
        return applyStatusChange(order, OrderStatus.CANCELLED, StatusChangeSource.USER,
                "Cancelled by user");
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = loadOwnedOrAdmin(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED
                && order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOperationException(
                    "Only PENDING or CANCELLED orders can be deleted");
        }
        orderRepository.delete(order);
        log.info("Order {} deleted", order.getOrderNumber());
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getStatusHistory(Long orderId) {
        Order order = loadOwnedOrAdmin(orderId);
        return orderMapper.toHistoryResponses(
                historyRepository.findByOrder_IdOrderByCreatedAtAsc(order.getId())
        );
    }

    // ---------- ADMIN ACTIONS ----------

    @Transactional(readOnly = true)
    public Page<OrderResponse> listAllOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return page.map(orderMapper::toResponse);
    }

    @Transactional
    public OrderResponse adminChangeStatus(Long orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return applyStatusChange(order, newStatus, StatusChangeSource.ADMIN,
                note != null ? note : "Changed by admin");
    }

    // ---------- WEBHOOK-DRIVEN TRANSITIONS ----------


    @Transactional
    public Order applyPaymentEvent(String orderNumber, String paymentReference,
                                   OrderStatus newStatus, String note) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for orderNumber " + orderNumber));

        order.setPaymentReference(paymentReference);
        applyStatusChange(order, newStatus, StatusChangeSource.WEBHOOK_PAYMENT, note);
        return order;
    }

    @Transactional
    public Order applyShipmentEvent(String orderNumber, String trackingNumber,
                                    OrderStatus newStatus, String note) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for orderNumber " + orderNumber));

        order.setShipmentTrackingNumber(trackingNumber);
        applyStatusChange(order, newStatus, StatusChangeSource.WEBHOOK_SHIPMENT, note);
        return order;
    }

    // ---------- INTERNAL HELPERS ----------

    private OrderResponse applyStatusChange(Order order, OrderStatus newStatus,
                                            StatusChangeSource source, String note) {
        OrderStatus prev = order.getStatus();
        if (prev == newStatus) {
            log.debug("Order {} already in status {} — no-op", order.getOrderNumber(), newStatus);
            return orderMapper.toResponse(order);
        }
        validateTransition(prev, newStatus);
        order.setStatus(newStatus);

        recordHistory(order, prev, newStatus, source, note);

        // fire-and-forget email (async, retried)
        try {
            emailNotificationService.sendOrderStatusChangedEmail(order, prev, newStatus);
        } catch (Exception ex) {
            log.warn("Failed to dispatch status-change email for order {}: {}",
                    order.getOrderNumber(), ex.getMessage());
        }

        return orderMapper.toResponse(order);
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        // Allowed transitions — simple state machine
        boolean ok = switch (from) {
            case PENDING -> to == OrderStatus.PAID
                    || to == OrderStatus.PAYMENT_FAILED
                    || to == OrderStatus.CANCELLED;
            case PAYMENT_FAILED -> to == OrderStatus.PENDING
                    || to == OrderStatus.PAID
                    || to == OrderStatus.CANCELLED;
            case PAID -> to == OrderStatus.SHIPPED
                    || to == OrderStatus.REFUNDED
                    || to == OrderStatus.CANCELLED;
            case SHIPPED -> to == OrderStatus.DELIVERED
                    || to == OrderStatus.REFUNDED;
            case DELIVERED -> to == OrderStatus.REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
        if (!ok) {
            throw new InvalidOperationException(
                    "Illegal status transition: " + from + " -> " + to);
        }
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to,
                               StatusChangeSource source, String note) {
        historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .source(source)
                .note(note)
                .build());
    }

    private OrderItem buildItem(OrderItemRequest ir) {
        BigDecimal subtotal = ir.getUnitPrice().multiply(BigDecimal.valueOf(ir.getQuantity()));
        return OrderItem.builder()
                .productName(ir.getProductName())
                .productSku(ir.getProductSku())
                .quantity(ir.getQuantity())
                .unitPrice(ir.getUnitPrice())
                .subtotal(subtotal)
                .build();
    }

    private BigDecimal sum(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Order loadOwnedOrAdmin(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        Long currentUserId = SecurityUtils.currentUserId();
        if (!SecurityUtils.isAdmin() && !order.getUser().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You do not have access to this order");
        }
        return order;
    }

    private String generateOrderNumber() {
        // ORD-YYYYMMDD-<8 hex chars> — collision-resistant and readable
        for (int i = 0; i < 5; i++) {
            String candidate = "ORD-" + LocalDate.now().format(ORDER_NO_FORMAT) + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique order number");
    }
}
