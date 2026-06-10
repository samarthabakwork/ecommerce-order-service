package com.ecommerce.OrderService.controllers.admin;

import com.ecommerce.OrderService.dto.request.OrderStatusUpdateRequestDTO;
import com.ecommerce.OrderService.dto.response.ApiResponse;
import com.ecommerce.OrderService.dto.response.OrderResponseDTO;
import com.ecommerce.OrderService.services.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
//@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping("/viewAllOrders")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getAllOrders() {
        List<OrderResponseDTO> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/viewOrder/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getOrderById(@PathVariable Long id) {
        OrderResponseDTO order = orderService.getOrderByIdForAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PatchMapping("/updateOrderStatus/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequestDTO request) {
        OrderResponseDTO order = orderService.updateOrderStatus(id, request);

        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }
}