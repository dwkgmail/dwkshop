package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.ProductSku;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findByProductId(Long productId);

    List<ProductSku> findByProductIdIn(Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sku from ProductSku sku where sku.id = :id")
    Optional<ProductSku> findByIdForUpdate(Long id);

    @Modifying
    @Query("update ProductSku sku set sku.lockedStock = :lockedStock, sku.updatedAt = CURRENT_TIMESTAMP where sku.id = :id")
    int updateLockedStock(@Param("id") Long id, @Param("lockedStock") Integer lockedStock);

    void deleteByProductId(Long productId);
}
