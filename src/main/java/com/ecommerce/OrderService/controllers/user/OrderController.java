package com.ecommerce.OrderService.controllers.user;

import com.ecommerce.OrderService.dto.request.PlaceOrderRequestDTO;
import com.ecommerce.OrderService.dto.response.ApiResponse;
import com.ecommerce.OrderService.dto.response.OrderResponseDTO;
import com.ecommerce.OrderService.services.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/placeOrder")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> placeOrder(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody PlaceOrderRequestDTO request) {
        OrderResponseDTO order = orderService.placeOrder(email, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", order));
    }

    @GetMapping("/viewOrder/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getOrder(
             Authentication authentication,
            @PathVariable Long id) {
        OrderResponseDTO order = orderService.getOrderById(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/viewOrderHistory")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getOrderHistory(
             Authentication authentication) {
        List<OrderResponseDTO> orders = orderService.getOrderHistory(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PatchMapping("/cancelOrder/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> cancelOrder(
             Authentication authentication,
            @PathVariable Long id) {
        OrderResponseDTO order = orderService.cancelOrder(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }
}
