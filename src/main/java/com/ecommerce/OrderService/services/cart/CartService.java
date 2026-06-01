package com.ecommerce.OrderService.services.cart;

import com.ecommerce.OrderService.dto.request.CartItemRequestDTO;
import com.ecommerce.OrderService.dto.request.UpdateCartRequestDTO;
import com.ecommerce.OrderService.dto.response.CartResponseDTO;
import com.ecommerce.OrderService.entities.Cart;

import java.util.List;


public interface CartService {
    CartResponseDTO addToCart(String userId, CartItemRequestDTO request);
    CartResponseDTO getCart(String userId);
    CartResponseDTO updateCartItem(String userId, UpdateCartRequestDTO request);
    CartResponseDTO removeFromCart(String userId, Long productId);
    void clearCart(String userId);
}
