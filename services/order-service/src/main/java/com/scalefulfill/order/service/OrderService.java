package com.scalefulfill.order.service;

import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderResponse;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrder(String orderId);

    OrderResponse cancelOrder(String orderId);
}
