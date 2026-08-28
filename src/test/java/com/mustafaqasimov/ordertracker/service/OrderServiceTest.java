package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.dto.request.CreateOrderRequest;
import com.mustafaqasimov.ordertracker.dto.request.OrderItemRequest;
import com.mustafaqasimov.ordertracker.dto.request.UpdateOrderRequest;
import com.mustafaqasimov.ordertracker.dto.response.DashboardResponse;
import com.mustafaqasimov.ordertracker.dto.response.OrderResponse;
import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.OrderStatusHistory;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.ActiveStatus;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.enums.Role;
import com.mustafaqasimov.ordertracker.enums.StatusChangeSource;
import com.mustafaqasimov.ordertracker.exception.error.InvalidOperationException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.exception.error.UnauthorizedException;
import com.mustafaqasimov.ordertracker.mapper.OrderMapper;
import com.mustafaqasimov.ordertracker.repository.OrderRepository;
import com.mustafaqasimov.ordertracker.repository.OrderStatusHistoryRepository;
import com.mustafaqasimov.ordertracker.repository.UserRepository;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import com.mustafaqasimov.ordertracker.websocket.OrderEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    private static final Long USER_ID = 7L;

    @Mock OrderRepository orderRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock UserRepository userRepository;
    @Mock OrderMapper orderMapper;
    @Mock EmailNotificationService emailNotificationService;
    @Mock OrderEventPublisher orderEventPublisher;

    @InjectMocks OrderService orderService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = TestFixtures.user(USER_ID, "user@test.local");
        TestFixtures.authenticate(USER_ID, Role.ROLE_USER);
    }

    @AfterEach
    void tearDown() {
        TestFixtures.clearAuthentication();
    }

    private CreateOrderRequest createRequest() {
        OrderItemRequest item = new OrderItemRequest("Wireless Mouse", "SKU-WM", 2, new BigDecimal("29.99"));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Main Street, Baku");
        request.setCurrency("usd");
        request.setItems(List.of(item));
        return request;
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("sums the item subtotals into the order total")
        void computesTotal() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
            when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(createRequest());

            ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(saved.capture());
            assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("59.98");
            assertThat(saved.getValue().getItems()).hasSize(1);
            assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(saved.getValue().getCustomerEmail()).isEqualTo("user@test.local");
        }

        @Test
        @DisplayName("upper-cases the currency and defaults it to USD when absent")
        void normalisesCurrency() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
            when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateOrderRequest request = createRequest();
            orderService.createOrder(request);

            request.setCurrency(null);
            orderService.createOrder(request);

            ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(saved.capture());
            assertThat(saved.getAllValues().get(0).getCurrency()).isEqualTo("USD");
            assertThat(saved.getAllValues().get(1).getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("generates an ORD-yyyyMMdd-XXXXXXXX number, retrying on collision")
        void generatesUniqueOrderNumber() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
            when(orderRepository.existsByOrderNumber(anyString())).thenReturn(true, false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(createRequest());

            ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(saved.capture());
            assertThat(saved.getValue().getOrderNumber()).matches("ORD-\\d{8}-[0-9A-F]{8}");
            verify(orderRepository, times(2)).existsByOrderNumber(anyString());
        }

        @Test
        @DisplayName("records the initial history row and pushes an ORDER_CREATED event")
        void recordsHistoryAndPublishes() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
            when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(createRequest());

            ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getFromStatus()).isNull();
            assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(history.getValue().getSource()).isEqualTo(StatusChangeSource.USER);

            verify(orderEventPublisher).orderCreated(any(Order.class));
        }

        @Test
        @DisplayName("fails when the authenticated user no longer exists")
        void unknownUser() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(createRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(orderEventPublisher);
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("a user cannot read somebody else's order")
        void foreignOrderIsRejected() {
            Order foreign = TestFixtures.order(1L, TestFixtures.user(99L, "other@test.local"), OrderStatus.PENDING);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> orderService.getMyOrder(1L))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("an admin may read any order")
        void adminCanReadAnyOrder() {
            TestFixtures.authenticate(1L, Role.ROLE_ADMIN);
            Order foreign = TestFixtures.order(1L, TestFixtures.user(99L, "other@test.local"), OrderStatus.PENDING);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(foreign));

            orderService.getMyOrder(1L);

            verify(orderMapper).toResponse(foreign);
        }

        @Test
        @DisplayName("a missing order is reported as not found")
        void missingOrder() {
            when(orderRepository.findWithItemsById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getMyOrder(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }
    }

    @Nested
    @DisplayName("listMyOrders")
    class ListMyOrders {

        @Test
        @DisplayName("filters by status only when one is given")
        void picksTheRightQuery() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.of(TestFixtures.order(1L, owner, OrderStatus.PENDING)));
            when(orderRepository.findByUser_Id(USER_ID, pageable)).thenReturn(page);
            when(orderRepository.findByUser_IdAndStatus(USER_ID, OrderStatus.PAID, pageable)).thenReturn(page);

            orderService.listMyOrders(null, pageable);
            orderService.listMyOrders(OrderStatus.PAID, pageable);

            verify(orderRepository).findByUser_Id(USER_ID, pageable);
            verify(orderRepository).findByUser_IdAndStatus(USER_ID, OrderStatus.PAID, pageable);
        }
    }

    @Nested
    @DisplayName("updateOrder")
    class UpdateOrder {

        @Test
        @DisplayName("recalculates the total when the items are replaced")
        void replacesItems() {
            Order order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
            order.addItem(TestFixtures.item("Old", 1, "10.00"));
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            UpdateOrderRequest request = new UpdateOrderRequest();
            request.setShippingAddress("New address 5");
            request.setItems(List.of(new OrderItemRequest("Keyboard", "SKU-KB", 3, new BigDecimal("20.00"))));

            orderService.updateOrder(1L, request);

            assertThat(order.getShippingAddress()).isEqualTo("New address 5");
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getTotalAmount()).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("leaves untouched fields alone when they are null or blank")
        void ignoresBlankFields() {
            Order order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
            String originalAddress = order.getShippingAddress();
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            UpdateOrderRequest request = new UpdateOrderRequest();
            request.setShippingAddress("   ");

            orderService.updateOrder(1L, request);

            assertThat(order.getShippingAddress()).isEqualTo(originalAddress);
        }

        @ParameterizedTest(name = "status {0} cannot be updated")
        @CsvSource({"PAID", "SHIPPED", "DELIVERED", "CANCELLED", "REFUNDED", "PAYMENT_FAILED"})
        @DisplayName("only a PENDING order may be updated")
        void onlyPendingIsEditable(OrderStatus status) {
            Order order = TestFixtures.order(1L, owner, status);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrder(1L, new UpdateOrderRequest()))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("PENDING");
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @ParameterizedTest(name = "{0} is cancellable")
        @CsvSource({"PENDING", "PAYMENT_FAILED"})
        void cancellableStates(OrderStatus status) {
            Order order = TestFixtures.order(1L, owner, status);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.cancelOrder(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderEventPublisher).statusChanged(order, status, OrderStatus.CANCELLED,
                    StatusChangeSource.USER, "Cancelled by user");
        }

        @ParameterizedTest(name = "{0} is not cancellable")
        @CsvSource({"PAID", "SHIPPED", "DELIVERED", "CANCELLED", "REFUNDED"})
        void nonCancellableStates(OrderStatus status) {
            Order order = TestFixtures.order(1L, owner, status);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOperationException.class);

            assertThat(order.getStatus()).isEqualTo(status);
            verifyNoInteractions(orderEventPublisher);
        }
    }

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrder {

        @ParameterizedTest(name = "{0} can be soft-deleted")
        @CsvSource({"PENDING", "CANCELLED"})
        void softDeletes(OrderStatus status) {
            Order order = TestFixtures.order(1L, owner, status);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.deleteOrder(1L);

            assertThat(order.getActive()).isEqualTo(ActiveStatus.INACTIVE);
            verify(orderRepository).save(order);
        }

        @ParameterizedTest(name = "{0} cannot be deleted")
        @CsvSource({"PAID", "SHIPPED", "DELIVERED", "REFUNDED", "PAYMENT_FAILED"})
        void refusesOtherStates(OrderStatus status) {
            Order order = TestFixtures.order(1L, owner, status);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.deleteOrder(1L))
                    .isInstanceOf(InvalidOperationException.class);

            assertThat(order.getActive()).isEqualTo(ActiveStatus.ACTIVE);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("state machine")
    class StateMachine {

        @ParameterizedTest(name = "{0} -> {1} is allowed")
        @CsvSource({
                "PENDING,PAID", "PENDING,PAYMENT_FAILED", "PENDING,CANCELLED",
                "PAYMENT_FAILED,PENDING", "PAYMENT_FAILED,PAID", "PAYMENT_FAILED,CANCELLED",
                "PAID,SHIPPED", "PAID,REFUNDED", "PAID,CANCELLED",
                "SHIPPED,DELIVERED", "SHIPPED,REFUNDED",
                "DELIVERED,REFUNDED"
        })
        void allowedTransitions(OrderStatus from, OrderStatus to) {
            Order order = TestFixtures.order(1L, owner, from);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.adminChangeStatus(1L, to, "note");

            assertThat(order.getStatus()).isEqualTo(to);
        }

        @ParameterizedTest(name = "{0} -> {1} is rejected")
        @CsvSource({
                "PENDING,SHIPPED", "PENDING,DELIVERED", "PENDING,REFUNDED",
                "PAID,DELIVERED", "PAID,PENDING",
                "SHIPPED,CANCELLED", "SHIPPED,PAID",
                "DELIVERED,SHIPPED", "DELIVERED,CANCELLED",
                "CANCELLED,PAID", "CANCELLED,PENDING",
                "REFUNDED,PAID", "REFUNDED,SHIPPED"
        })
        void rejectedTransitions(OrderStatus from, OrderStatus to) {
            Order order = TestFixtures.order(1L, owner, from);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.adminChangeStatus(1L, to, "note"))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Illegal status transition");

            assertThat(order.getStatus()).isEqualTo(from);
            verifyNoInteractions(orderEventPublisher, emailNotificationService, historyRepository);
        }

        @Test
        @DisplayName("re-applying the same status is a no-op, not an error")
        void sameStatusIsIdempotent() {
            Order order = TestFixtures.order(1L, owner, OrderStatus.PAID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.adminChangeStatus(1L, OrderStatus.PAID, "again");

            verifyNoInteractions(historyRepository, orderEventPublisher, emailNotificationService);
        }

        @Test
        @DisplayName("a failing e-mail never breaks the status change")
        void emailFailureIsSwallowed() {
            Order order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
            org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                    .when(emailNotificationService)
                    .sendOrderStatusChangedEmail(any(), any(), any());

            orderService.adminChangeStatus(1L, OrderStatus.PAID, null);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderEventPublisher).statusChanged(order, OrderStatus.PENDING, OrderStatus.PAID,
                    StatusChangeSource.ADMIN, "Changed by admin");
        }
    }

    @Nested
    @DisplayName("webhook-driven transitions")
    class WebhookTransitions {

        @Test
        @DisplayName("a payment event stores the reference and moves the status")
        void paymentEvent() {
            Order order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
            when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

            Order result = orderService.applyPaymentEvent("ORD-1", "pi_123", OrderStatus.PAID, "paid");

            assertThat(result.getPaymentReference()).isEqualTo("pi_123");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderEventPublisher).statusChanged(order, OrderStatus.PENDING, OrderStatus.PAID,
                    StatusChangeSource.WEBHOOK_PAYMENT, "paid");
        }

        @Test
        @DisplayName("a shipment event stores the tracking number and moves the status")
        void shipmentEvent() {
            Order order = TestFixtures.order(1L, owner, OrderStatus.PAID);
            when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

            Order result = orderService.applyShipmentEvent("ORD-1", "TRK-9", OrderStatus.SHIPPED, "shipped");

            assertThat(result.getShipmentTrackingNumber()).isEqualTo("TRK-9");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(orderEventPublisher).statusChanged(order, OrderStatus.PAID, OrderStatus.SHIPPED,
                    StatusChangeSource.WEBHOOK_SHIPMENT, "shipped");
        }

        @Test
        @DisplayName("an unknown order number is reported as not found")
        void unknownOrderNumber() {
            when(orderRepository.findByOrderNumber("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.applyPaymentEvent("nope", "pi", OrderStatus.PAID, "x"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("nope");
        }
    }

    @Nested
    @DisplayName("admin queries")
    class AdminQueries {

        @Test
        @DisplayName("listAllOrders filters by status only when one is given")
        void listAllOrders() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.<Order>of());
            when(orderRepository.findAll(pageable)).thenReturn(page);
            when(orderRepository.findByStatus(OrderStatus.PAID, pageable)).thenReturn(page);

            orderService.listAllOrders(null, pageable);
            orderService.listAllOrders(OrderStatus.PAID, pageable);

            verify(orderRepository).findAll(pageable);
            verify(orderRepository).findByStatus(OrderStatus.PAID, pageable);
        }

        @Test
        @DisplayName("the dashboard counts every status it reports")
        void dashboard() {
            when(orderRepository.count()).thenReturn(10L);
            when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(4L);
            when(orderRepository.countByStatus(OrderStatus.PAID)).thenReturn(3L);
            when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(2L);
            when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(1L);

            DashboardResponse response = orderService.getDashboardStatistics();

            assertThat(response.getTotalOrders()).isEqualTo(10L);
            assertThat(response.getPendingOrders()).isEqualTo(4L);
            assertThat(response.getPaidOrders()).isEqualTo(3L);
            assertThat(response.getDeliveredOrders()).isEqualTo(2L);
            assertThat(response.getCancelledOrders()).isEqualTo(1L);
        }

        @Test
        @DisplayName("history for an unknown order is reported as not found")
        void historyForMissingOrder() {
            when(orderRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderHistoryForAdmin(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getStatusHistory reads the rows of the order it just authorised")
    void statusHistory() {
        Order order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrder_IdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        orderService.getStatusHistory(1L);

        verify(historyRepository).findByOrder_IdOrderByCreatedAtAsc(1L);
        verify(orderMapper).toHistoryResponses(eq(List.of()));
    }

    @Test
    @DisplayName("an unauthenticated caller cannot create an order")
    void unauthenticated() {
        TestFixtures.clearAuthentication();

        assertThatThrownBy(() -> orderService.createOrder(createRequest()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("the response always comes from the mapper")
    void mapsResponse() {
        Order order = TestFixtures.order(1L, owner, OrderStatus.PENDING);
        OrderResponse expected = OrderResponse.builder().id(1L).build();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(expected);

        assertThat(orderService.getMyOrder(1L)).isSameAs(expected);
    }
}
