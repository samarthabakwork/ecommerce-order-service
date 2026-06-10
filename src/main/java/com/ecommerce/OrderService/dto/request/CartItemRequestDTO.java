package com.ecommerce.OrderService.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemRequestDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

//    @NotBlank(message = "Product name is required")
//    private String productName;

//    @NotNull(message = "Price is required")
//    @Positive(message = "Price must be positive")
//    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
