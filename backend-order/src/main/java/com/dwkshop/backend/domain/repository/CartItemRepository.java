package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByIdDesc(Long userId);

    Optional<CartItem> findByUserIdAndSkuId(Long userId, Long skuId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);
}
