package com.baeldung.axon.gui;

import com.baeldung.axon.coreapi.queries.Order;

import java.util.Map;

import static com.baeldung.axon.gui.OrderStatusResponse.toResponse;

public class OrderResponse{
    private final String orderId;
    private final Map<String, Integer> products;
    private final OrderStatusResponse orderStatus;

    OrderResponse(Order order){
        this.orderId = order.getOrderId();
        this.products = order.getProducts();
        this.orderStatus = toResponse(order.getOrderStatus());
    }

    public String getOrderId() {
        return orderId;
    }

    public Map<String, Integer> getProducts() {
        return products;
    }

    public OrderStatusResponse getOrderStatus() {
        return orderStatus;
    }
}
