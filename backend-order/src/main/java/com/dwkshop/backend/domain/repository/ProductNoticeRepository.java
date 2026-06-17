package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.ProductNotice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductNoticeRepository extends JpaRepository<ProductNotice, Long> {

    Optional<ProductNotice> findByProductIdAndEnabledFlagTrue(Long productId);

    List<ProductNotice> findByProductIdInAndEnabledFlagTrue(Collection<Long> productIds);
}
