package com.ecommerce.OrderService.services.order;



import com.ecommerce.OrderService.dto.request.OrderStatusUpdateRequestDTO;
import com.ecommerce.OrderService.dto.request.PlaceOrderRequestDTO;
import com.ecommerce.OrderService.dto.request.ProductDTO;
import com.ecommerce.OrderService.dto.request.StockUpdateRequestDTO;
import com.ecommerce.OrderService.dto.response.OrderItemResponseDTO;
import com.ecommerce.OrderService.dto.response.OrderResponseDTO;
import com.ecommerce.OrderService.entities.Cart;
import com.ecommerce.OrderService.entities.CartItem;
import com.ecommerce.OrderService.entities.Order;
import com.ecommerce.OrderService.entities.OrderItem;
import com.ecommerce.OrderService.enums.OrderStatus;
import com.ecommerce.OrderService.exception.BadRequestException;
import com.ecommerce.OrderService.exception.OutOfStockException;
import com.ecommerce.OrderService.exception.ResourceNotFoundException;
import com.ecommerce.OrderService.exception.ServiceUnavailableException;
import com.ecommerce.OrderService.repositories.CartRepository;
import com.ecommerce.OrderService.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
    public OrderResponseDTO placeOrder(String userId, PlaceOrderRequestDTO request) {
        //get cart
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot place order with an empty cart");
        }


        //store all productids
        List<Long> productIds=cart.getItems().stream()
                .map(CartItem::getProductId)
                .toList();

        List<ProductDTO> products;

        try{

            //get all products requested from product service
            products=restTemplate.exchange(
                    "http://PRODUCTSERVICE/internal/products/getProductsById",
                    HttpMethod.POST,
                    new HttpEntity<>(productIds),
                    new ParameterizedTypeReference<List<ProductDTO>>() {}
                    ).getBody();


        }
        catch(HttpClientErrorException.NotFound ex){
            throw new ResourceNotFoundException(ex.getResponseBodyAsString());
        }
        catch (HttpClientErrorException ex){
            throw new BadRequestException(ex.getResponseBodyAsString());
        }
        catch(RestClientException ex){
            ex.printStackTrace();
            throw new ServiceUnavailableException("Product Service Unavailable");
        }

        Map<Long,ProductDTO> productMap=new HashMap<>();

        //store all products inside map for easy retrieval
        for(ProductDTO product:products){
            productMap.put(product.getProductId(),product);
        }

        List<OrderItem> orderItems=new ArrayList<>();


        for(CartItem cartItem:cart.getItems()) {

            //get Product from Map
            ProductDTO product=productMap.get(cartItem.getProductId());

            if (product.getStock()<cartItem.getQuantity()){
                throw new OutOfStockException("Only "+product.getStock()+" items available");
            }
            BigDecimal subtotal=product.getProductPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem orderItem=OrderItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getProductName())
                    .price(product.getProductPrice())
                    .quantity(cartItem.getQuantity())
                    .subtotal(subtotal)
                    .build();

            orderItems.add(orderItem);
        };


        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //Order created
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .orderDate(LocalDateTime.now())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.getOrderItems().addAll(orderItems);

        List<StockUpdateRequestDTO> stockRequests=order.getOrderItems().stream()
                .map(orderItem ->
                                StockUpdateRequestDTO.builder()
                                        .productId(orderItem.getProductId())
                                        .quantity(orderItem.getQuantity())
                                        .build()
                        ).toList();

        log.info("Reducing stock for products: {}",productIds);

        try {
            restTemplate.put(
                    "http://PRODUCTSERVICE/internal/products/decreaseStockBulk",
                    stockRequests
            );
        }
        catch (HttpClientErrorException ex){
            ex.printStackTrace();
            throw new BadRequestException(ex.getResponseBodyAsString());
        }
        catch(RestClientException ex){
            ex.printStackTrace();
            throw new ServiceUnavailableException("Service is Unavailable");
        }

        //Order saved
        Order savedOrder = orderRepository.save(order);

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
    @Transactional
    public OrderResponseDTO cancelOrder(String userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot cancel an order that is already " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled");
        }


        List<StockUpdateRequestDTO> stockRequests=order.getOrderItems().stream()
                .map(item->StockUpdateRequestDTO.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build()
                )
                        .toList();
        List<Long> productIds=stockRequests.stream()
                                    .map(StockUpdateRequestDTO::getProductId)
                                    .toList();

        log.info("Increasing stock for products: {}",productIds);

        try {
            restTemplate.put(
                    "http://PRODUCTSERVICE/internal/products/increaseStockBulk",
                    stockRequests
            );
        }
        catch(HttpClientErrorException ex){
            throw new BadRequestException(ex.getResponseBodyAsString());
        }
            catch(RestClientException ex){
            throw new ServiceUnavailableException("Service is Unavailable");
        }

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
    @Transactional
    public OrderResponseDTO updateOrderStatus(Long orderId, OrderStatusUpdateRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if(request.getStatus()==OrderStatus.CANCELLED &&
        order.getStatus()!=OrderStatus.CANCELLED){

            List<StockUpdateRequestDTO> stockRequests=new ArrayList<>();

            order.getOrderItems().forEach(item->{
                StockUpdateRequestDTO stockRequest=
                        StockUpdateRequestDTO.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .build();

                stockRequests.add(stockRequest);
            });

            try {
                restTemplate.put(
                        "http://PRODUCTSERVICE/internal/products/increaseStockBulk",
                        stockRequests
                );
            }
            catch(HttpClientErrorException ex){
                throw new BadRequestException(ex.getResponseBodyAsString());
            }
            catch(RestClientException ex){
                throw new ServiceUnavailableException("Service is Unavailable");
            }
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