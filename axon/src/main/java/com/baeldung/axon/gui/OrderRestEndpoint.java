package com.baeldung.axon.gui;

import com.baeldung.axon.coreapi.commands.AddProductCommand;
import com.baeldung.axon.coreapi.commands.ConfirmOrderCommand;
import com.baeldung.axon.coreapi.commands.CreateOrderCommand;
import com.baeldung.axon.coreapi.commands.DecrementProductCountCommand;
import com.baeldung.axon.coreapi.commands.IncrementProductCountCommand;
import com.baeldung.axon.coreapi.commands.ShipOrderCommand;
import com.baeldung.axon.coreapi.queries.FindAllOrderedProductsQuery;
import com.baeldung.axon.coreapi.queries.Order;
import com.baeldung.axon.coreapi.queries.OrderUpdatesQuery;
import com.baeldung.axon.coreapi.queries.TotalProductsShippedQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
public class OrderRestEndpoint {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public OrderRestEndpoint(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping("/ship-order")
    public CompletableFuture<Void> shipOrder() {
        String orderId = UUID.randomUUID().toString();
        return commandGateway.send(new CreateOrderCommand(orderId))
                             .thenCompose(result -> commandGateway.send(new AddProductCommand(orderId, "Deluxe Chair")))
                             .thenCompose(result -> commandGateway.send(new ConfirmOrderCommand(orderId)))
                             .thenCompose(result -> commandGateway.send(new ShipOrderCommand(orderId)));
    }

    @PostMapping("/ship-unconfirmed-order")
    public CompletableFuture<Void> shipUnconfirmedOrder() {
        String orderId = UUID.randomUUID().toString();
        return commandGateway.send(new CreateOrderCommand(orderId))
                             .thenCompose(result -> commandGateway.send(new AddProductCommand(orderId, "Deluxe Chair")))
                             // This throws an exception, as an Order cannot be shipped if it has not been confirmed yet.
                             .thenCompose(result -> commandGateway.send(new ShipOrderCommand(orderId)));
    }

    @PostMapping("/order")
    public CompletableFuture<String> createOrder() {
        return createOrder(UUID.randomUUID().toString());
    }

    @PostMapping("/order/{order-id}")
    public CompletableFuture<String> createOrder(@PathVariable("order-id") String orderId) {
        return commandGateway.send(new CreateOrderCommand(orderId));
    }

    @PostMapping("/order/{order-id}/product/{product-id}")
    public CompletableFuture<Void> addProduct(@PathVariable("order-id") String orderId,
                                              @PathVariable("product-id") String productId) {
        return commandGateway.send(new AddProductCommand(orderId, productId));
    }

    @PostMapping("/order/{order-id}/product/{product-id}/increment")
    public CompletableFuture<Void> incrementProduct(@PathVariable("order-id") String orderId,
                                                    @PathVariable("product-id") String productId) {
        return commandGateway.send(new IncrementProductCountCommand(orderId, productId));
    }

    @PostMapping("/order/{order-id}/product/{product-id}/decrement")
    public CompletableFuture<Void> decrementProduct(@PathVariable("order-id") String orderId,
                                                    @PathVariable("product-id") String productId) {
        return commandGateway.send(new DecrementProductCountCommand(orderId, productId));
    }

    @PostMapping("/order/{order-id}/confirm")
    public CompletableFuture<Void> confirmOrder(@PathVariable("order-id") String orderId) {
        return commandGateway.send(new ConfirmOrderCommand(orderId));
    }

    @PostMapping("/order/{order-id}/ship")
    public CompletableFuture<Void> shipOrder(@PathVariable("order-id") String orderId) {
        return commandGateway.send(new ShipOrderCommand(orderId));
    }

    @GetMapping("/all-orders")
    public CompletableFuture<List<OrderResponse>> findAllOrders() {
        return queryGateway.query(new FindAllOrderedProductsQuery(), ResponseTypes.multipleInstancesOf(Order.class))
                .thenApply(r -> r.stream().map(OrderResponse::new).collect(Collectors.toList()));
    }

    @GetMapping("/total-shipped/{product-id}")
    public Integer totalShipped(@PathVariable("product-id") String productId) {
        return queryGateway.scatterGather(new TotalProductsShippedQuery(productId), ResponseTypes.instanceOf(Integer.class), 10L, TimeUnit.SECONDS)
                .reduce(0, Integer::sum);
    }

    @GetMapping(path = "/order-updates/{order-id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderResponse> orderUpdates(@PathVariable("order-id") String orderId) {
        return subscriptionQuery(new OrderUpdatesQuery(orderId), ResponseTypes.instanceOf(Order.class))
                .map(OrderResponse::new);
    }

    private  <Q, R> Flux<R> subscriptionQuery(Q query, ResponseType<R> resultType) {
        SubscriptionQueryResult<R,R> result = queryGateway.subscriptionQuery(query, resultType, resultType);
        return result.initialResult().concatWith(result.updates()).doFinally(signal -> result.close());
    }
}
