package com.ecommerce.OrderService.controllers.user;

import com.ecommerce.OrderService.dto.request.CartItemRequestDTO;
import com.ecommerce.OrderService.dto.request.UpdateCartRequestDTO;
import com.ecommerce.OrderService.dto.response.ApiResponse;
import com.ecommerce.OrderService.dto.response.CartResponseDTO;
import com.ecommerce.OrderService.services.cart.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/addToCart")
    public ResponseEntity<ApiResponse<CartResponseDTO>> addToCart(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CartItemRequestDTO request) {
        CartResponseDTO cart = cartService.addToCart(email, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    @GetMapping("/viewCart")
    public ResponseEntity<ApiResponse<CartResponseDTO>> getCart(
             Authentication authentication) {
        CartResponseDTO cart = cartService.getCart(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PutMapping("/updateCart")
    public ResponseEntity<ApiResponse<CartResponseDTO>> updateCart(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody UpdateCartRequestDTO request) {
        CartResponseDTO cart = cartService.updateCartItem(email, request);
        return ResponseEntity.ok(ApiResponse.success("Cart updated", cart));
    }

    @DeleteMapping("/removeFromCart/{productId}")
    public ResponseEntity<ApiResponse<CartResponseDTO>> removeFromCart(
             Authentication authentication,
            @PathVariable Long productId) {
        CartResponseDTO cart = cartService.removeFromCart(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }

    @DeleteMapping("/clearCart")
    public ResponseEntity<ApiResponse<Void>> clearCart(
             Authentication authentication) {
        cartService.clearCart(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", null));
    }
}
