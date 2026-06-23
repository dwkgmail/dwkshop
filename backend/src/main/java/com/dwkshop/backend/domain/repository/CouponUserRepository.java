package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.CouponUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUserRepository extends JpaRepository<CouponUser, Long> {

    List<CouponUser> findByUserIdAndUserCouponStatus(Long userId, String userCouponStatus);

    Optional<CouponUser> findByIdAndUserIdAndUserCouponStatus(Long id, Long userId, String userCouponStatus);

    long countByUserId(Long userId);
}
