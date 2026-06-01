package com.ecommerce.OrderService.services.order;

import com.ecommerce.OrderService.dto.request.OrderStatusUpdateRequestDTO;
import com.ecommerce.OrderService.dto.request.PlaceOrderRequestDTO;
import com.ecommerce.OrderService.dto.response.OrderResponseDTO;

import java.util.List;

public interface OrderService {
    OrderResponseDTO placeOrder(String userId, PlaceOrderRequestDTO request);
    OrderResponseDTO getOrderById(String userId, Long orderId);
    List<OrderResponseDTO> getOrderHistory(String userId);
    OrderResponseDTO cancelOrder(String userId, Long orderId);

    // Admin
    List<OrderResponseDTO> getAllOrders();
    OrderResponseDTO getOrderByIdForAdmin(Long orderId);
    OrderResponseDTO updateOrderStatus(Long orderId, OrderStatusUpdateRequestDTO request);
}
