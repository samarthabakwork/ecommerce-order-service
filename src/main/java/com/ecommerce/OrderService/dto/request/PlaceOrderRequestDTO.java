package com.ecommerce.OrderService.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequestDTO {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;
}
