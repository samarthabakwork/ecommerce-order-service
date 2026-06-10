package com.ecommerce.OrderService.dto.response;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponseDTO {
    private Long id;
    private String userId;
    private List<CartItemResponseDTO> items;
    private BigDecimal totalAmount;
    private int totalItems;
}
