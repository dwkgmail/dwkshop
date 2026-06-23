package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    boolean existsByCouponCode(String couponCode);
}
