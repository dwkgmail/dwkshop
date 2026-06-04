package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.ProductSku;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findByProductId(Long productId);

    List<ProductSku> findByProductIdIn(Collection<Long> productIds);

    void deleteByProductId(Long productId);
}
