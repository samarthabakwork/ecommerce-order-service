package com.ecommerce.OrderService.repositories;

import com.ecommerce.OrderService.entities.Cart;
import com.ecommerce.OrderService.entities.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem,Long> {
    Optional<CartItem> findByCartAndProductId(Cart cart, Long productId);
    void deleteByCartIdAndProductId(Long cartId,Long productId);
}
