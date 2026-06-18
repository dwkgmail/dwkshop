package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.ProductRefundCommand;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRefundCommandRepository extends JpaRepository<ProductRefundCommand, Long> {

    Optional<ProductRefundCommand> findByCommandNo(String commandNo);
}
