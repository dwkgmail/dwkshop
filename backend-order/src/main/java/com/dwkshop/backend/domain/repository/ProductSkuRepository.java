package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.ProductSku;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findByProductId(Long productId);

    List<ProductSku> findByProductIdIn(Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sku from ProductSku sku where sku.id = :id")
    Optional<ProductSku> findByIdForUpdate(Long id);

    void deleteByProductId(Long productId);
}
