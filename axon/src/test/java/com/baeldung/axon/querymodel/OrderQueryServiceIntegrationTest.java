package com.baeldung.axon.querymodel;

import com.baeldung.axon.OrderApplication;
import com.baeldung.axon.coreapi.events.OrderConfirmedEvent;
import com.baeldung.axon.coreapi.events.OrderCreatedEvent;
import com.baeldung.axon.coreapi.events.OrderShippedEvent;
import com.baeldung.axon.coreapi.events.ProductAddedEvent;
import com.baeldung.axon.coreapi.events.ProductCountDecrementedEvent;
import com.baeldung.axon.coreapi.events.ProductCountIncrementedEvent;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.isNull;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = OrderApplication.class)
class OrderQueryServiceIntegrationTest {

    @Autowired
    OrderQueryService queryService;

    @Autowired
    EventGateway eventGateway;

    private String orderId;
    private final String productId = "Deluxe Chair";

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID().toString();
    }

    @Test
    @DirtiesContext
    void givenOrderCreatedEventSend_whenCallingAllOrders_thenOneCreatedOrderIsReturned() {
        sendOrderCreatedEvent();

        AtomicReference<List<OrderResponse>> result = new AtomicReference<>();
        await().atMost(Duration.of(1, ChronoUnit.SECONDS)).until(
                () -> {
                    result.set(queryService.findAllOrders().get());
                    return !isNull(result.get()) && result.get().size() == 1;
                }
        );
        OrderResponse response = result.get().get(0);
        assertEquals(orderId, response.getOrderId());
        assertEquals(OrderStatusResponse.CREATED, response.getOrderStatus());
        assertTrue(response.getProducts().isEmpty());
    }

    @Test
    @DirtiesContext
    void givenTwoDeluxeChairsShipped_whenCallingAllShippedChairs_then234PlusTwoIsReturned(){
        orderAndShipTwoDeluxeChairs();

        await().atMost(Duration.of(1, ChronoUnit.SECONDS)).until(
                () -> queryService.totalShipped(productId) == 236
        );
    }

    @Test
    @DirtiesContext
    void givenOrdersAreUpdated_whenCallingOrderUpdates_thenUpdatesReturned(){
        sendOrderCreatedEvent();
        await().atMost(Duration.of(1, ChronoUnit.SECONDS)).until(
                () -> !queryService.findAllOrders().get().isEmpty()
        );

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(this::addIncrementDecrementConfirmAndShip, 100L, TimeUnit.MILLISECONDS);
        try {
            StepVerifier.create(queryService.orderUpdates(orderId))
                        .assertNext(order -> assertTrue(order.getProducts().isEmpty()))
                        .assertNext(order -> assertEquals(1, order.getProducts().get(productId)))
                        .assertNext(order -> assertEquals(2, order.getProducts().get(productId)))
                        .assertNext(order -> assertEquals(1, order.getProducts().get(productId)))
                        .assertNext(order -> assertEquals(OrderStatusResponse.CONFIRMED, order.getOrderStatus()))
                        .assertNext(order -> assertEquals(OrderStatusResponse.SHIPPED, order.getOrderStatus()))
                        .thenCancel()
                        .verify();
        } finally {
            executor.shutdown();
        }
    }

    private void orderAndShipTwoDeluxeChairs(){
        sendOrderCreatedEvent();
        sendProductAddedEvent();
        sendProductCountIncrementEvent();
        sendOrderConfirmedEvent();
        sendOrderShippedEvent();
    }

    private void addIncrementDecrementConfirmAndShip(){
        sendProductAddedEvent();
        sendProductCountIncrementEvent();
        sendProductCountDecrement();
        sendOrderConfirmedEvent();
        sendOrderShippedEvent();
    }


    private void sendOrderCreatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);
        eventGateway.publish(event);
    }

    private void sendProductAddedEvent() {
        ProductAddedEvent event = new ProductAddedEvent(orderId, productId);
        eventGateway.publish(event);
    }

    private void sendProductCountIncrementEvent() {
        ProductCountIncrementedEvent event = new ProductCountIncrementedEvent(orderId, productId);
        eventGateway.publish(event);
    }

    private void sendProductCountDecrement() {
        ProductCountDecrementedEvent event = new ProductCountDecrementedEvent(orderId, productId);
        eventGateway.publish(event);
    }

    private void sendOrderConfirmedEvent() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(orderId);
        eventGateway.publish(event);
    }

    private void sendOrderShippedEvent() {
        OrderShippedEvent event = new OrderShippedEvent(orderId);
        eventGateway.publish(event);
    }
}
