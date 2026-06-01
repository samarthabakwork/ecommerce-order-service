package com.ecommerce.OrderService.services.order;



import com.ecommerce.OrderService.dto.request.OrderStatusUpdateRequestDTO;
import com.ecommerce.OrderService.dto.request.PlaceOrderRequestDTO;
import com.ecommerce.OrderService.dto.request.StockUpdateRequestDTO;
import com.ecommerce.OrderService.dto.response.CartItemResponseDTO;
import com.ecommerce.OrderService.dto.response.OrderItemResponseDTO;
import com.ecommerce.OrderService.dto.response.OrderResponseDTO;
import com.ecommerce.OrderService.entities.Cart;
import com.ecommerce.OrderService.entities.Order;
import com.ecommerce.OrderService.entities.OrderItem;
import com.ecommerce.OrderService.enums.OrderStatus;
import com.ecommerce.OrderService.exception.BadRequestException;
import com.ecommerce.OrderService.exception.ResourceNotFoundException;
import com.ecommerce.OrderService.repositories.CartRepository;
import com.ecommerce.OrderService.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final RestTemplate restTemplate;

    @Override
    public OrderResponseDTO placeOrder(String userId, PlaceOrderRequestDTO request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot place order with an empty cart");
        }

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> {
                    BigDecimal subtotal = cartItem.getPrice()
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                    return OrderItem.builder()
                            .productId(cartItem.getProductId())
                            .productName(cartItem.getProductName())
                            .price(cartItem.getPrice())
                            .quantity(cartItem.getQuantity())
                            .subtotal(subtotal)
                            .build();

                })
                .toList();

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(userId)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .orderDate(LocalDateTime.now())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.getOrderItems().addAll(orderItems);

        Order savedOrder = orderRepository.save(order);
        orderItems.forEach(item -> {

            StockUpdateRequestDTO stockRequest =
                    StockUpdateRequestDTO.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build();

            log.info("Reducing stock for productId: {}", item.getProductId());

            restTemplate.put(
                    "http://PRODUCTSERVICE/internal/products/reduceStock",
                    stockRequest
            );
        });
        // Clear cart after successful order
        cart.getItems().clear();
        cartRepository.save(cart);

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(String userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrderHistory(String userId) {
        return orderRepository.findByUserIdOrderByOrderDateDesc(userId)
                .stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    @Override
    public OrderResponseDTO cancelOrder(String userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot cancel an order that is already " + order.getStatus());
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled");
        }

        order.getOrderItems().forEach(item -> {

            StockUpdateRequestDTO request =
                    StockUpdateRequestDTO.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build();

            log.info("Increasing stock for productId: {}", item.getProductId());

            restTemplate.put(
                    "http://PRODUCTSERVICE/internal/products/increaseStock",
                    request
            );
        });

        order.setStatus(OrderStatus.CANCELLED);

        log.info("Order cancelled successfully with id: {}", orderId);

        return mapToOrderResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderByIdForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    @Override
    public OrderResponseDTO updateOrderStatus(Long orderId, OrderStatusUpdateRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if(request.getStatus()==OrderStatus.CANCELLED &&
        order.getStatus()!=OrderStatus.CANCELLED){
            order.getOrderItems().forEach(item->{
                StockUpdateRequestDTO stockRequest=
                        StockUpdateRequestDTO.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .build();

                restTemplate.put(
                        "http://PRODUCTSERVICE/internal/products/increaseStock",
                        stockRequest
                );
            });
        }
        order.setStatus(request.getStatus());
        return mapToOrderResponse(orderRepository.save(order));
    }

    private OrderResponseDTO mapToOrderResponse(Order order) {
        List<OrderItemResponseDTO> items = order.getOrderItems().stream()
                .map(item -> OrderItemResponseDTO.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .orderDate(order.getOrderDate())
                .orderItems(items)
                .build();
    }
}