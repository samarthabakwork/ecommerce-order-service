package com.ecommerce.OrderService.services.cart;


import com.ecommerce.OrderService.dto.request.CartItemRequestDTO;
import com.ecommerce.OrderService.dto.request.UpdateCartRequestDTO;
import com.ecommerce.OrderService.dto.response.CartItemResponseDTO;
import com.ecommerce.OrderService.dto.response.CartResponseDTO;
import com.ecommerce.OrderService.entities.Cart;
import com.ecommerce.OrderService.entities.CartItem;
import com.ecommerce.OrderService.dto.request.ProductDTO;
import com.ecommerce.OrderService.exception.OutOfStockException;
import com.ecommerce.OrderService.exception.ResourceNotFoundException;
import com.ecommerce.OrderService.repositories.CartItemRepository;
import com.ecommerce.OrderService.repositories.CartRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


import java.math.BigDecimal;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final RestTemplate restTemplate;

    @Override
    public CartResponseDTO addToCart(String userId, CartItemRequestDTO request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));

        ProductDTO product;

        try {

            product = restTemplate.getForObject(
                    "http://PRODUCTSERVICE/products/viewProduct/" + request.getProductId(),
                    ProductDTO.class
            );

        } catch (HttpClientErrorException.NotFound ex) {

            throw new ResourceNotFoundException(
                    "Product not found with id : " + request.getProductId()
            );
        }

            cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                    .ifPresentOrElse(
                            existingItem -> {
                                int updatedQuantity=existingItem.getQuantity() + request.getQuantity();
                                if(updatedQuantity>product.getStock()){
                                    throw new OutOfStockException( "Only " + product.getStock() + " items available");
                                }
                                existingItem.setQuantity(updatedQuantity);
                            },
                            () -> {

                                if(request.getQuantity()>product.getStock()){
                                    throw new OutOfStockException("Only " + product.getStock() + " items available");
                                }

                                CartItem newItem = CartItem.builder()
                                        .cart(cart)
                                        .productId(request.getProductId())
                                        .productName(product.getProductName())
                                        .price(product.getProductPrice())
                                        .quantity(request.getQuantity())
                                        .build();
                                cart.getItems().add(newItem);
                            }
                    );
            cartRepository.save(cart);
            log.info("cart saved successfully");

        return mapToCartResponse(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDTO getCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        return mapToCartResponse(cart);
    }

    @Override
    public CartResponseDTO updateCartItem(String userId, UpdateCartRequestDTO request) {
        Cart cart = getCartByUserId(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in cart"));
        ProductDTO product;

        try {
            product = restTemplate.getForObject(
                    "http://PRODUCTSERVICE/products/viewProduct/" + request.getProductId(),
                    ProductDTO.class
            );
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException(
                    "Product not found with id : " + request.getProductId()
            );
        }

        if(request.getQuantity() > product.getStock()) {
            throw new OutOfStockException(
                    "Only " + product.getStock() + " items available"
            );
        }
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return mapToCartResponse(cart);
    }

    @Override
    public CartResponseDTO removeFromCart(String userId, Long productId) {
        Cart cart = getCartByUserId(userId);
        cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in cart"));
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        return mapToCartResponse(cart);
    }

    @Override
    public void clearCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));
    }

    private Cart getCartByUserId(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));
    }

    private CartResponseDTO mapToCartResponse(Cart cart) {
        List<CartItemResponseDTO> itemResponses = cart.getItems().stream()
                .map(item -> CartItemResponseDTO.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(CartItemResponseDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponseDTO.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalAmount(total)
                .totalItems(itemResponses.size())
                .build();
    }
}