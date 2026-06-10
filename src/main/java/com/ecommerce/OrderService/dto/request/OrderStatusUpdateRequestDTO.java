package com.ecommerce.OrderService.dto.request;

import com.ecommerce.OrderService.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateRequestDTO {

    @NotNull(message = "Status is required")
    private OrderStatus status;
}
