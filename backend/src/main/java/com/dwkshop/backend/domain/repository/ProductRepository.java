package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByDeletedFlagFalseOrderByIdDesc();

    List<Product> findByDeletedFlagFalseAndSaleStatusOrderByIdDesc(String saleStatus);

    List<Product> findByDeletedFlagFalseAndSaleStatusAndCategoryIdOrderByIdDesc(String saleStatus, Long categoryId);

    List<Product> findByDeletedFlagFalseAndSaleStatusAndNameContainingOrderByIdDesc(String saleStatus, String name);
}
