package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.ProductCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByStatusOrderBySortOrderAscIdAsc(String status);
}
