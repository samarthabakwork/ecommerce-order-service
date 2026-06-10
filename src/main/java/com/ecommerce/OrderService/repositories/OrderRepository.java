package com.ecommerce.OrderService.repositories;


import com.ecommerce.OrderService.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByOrderDateDesc(String userId);
    Optional<Order> findByIdAndUserId(Long id,String userId);

}
