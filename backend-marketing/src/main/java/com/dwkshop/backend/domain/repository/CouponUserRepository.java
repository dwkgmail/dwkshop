package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.CouponUser;
import com.dwkshop.backend.marketing.dto.UserCouponCountResponse;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CouponUserRepository extends JpaRepository<CouponUser, Long> {

    List<CouponUser> findByUserIdAndUserCouponStatus(Long userId, String userCouponStatus);

    List<CouponUser> findByUserIdAndUserCouponStatusIn(Long userId, List<String> userCouponStatuses);

    @Query("""
        select new com.dwkshop.backend.marketing.dto.UserCouponCountResponse(couponUser.userId, count(couponUser))
        from CouponUser couponUser
        where couponUser.userId in :userIds
        group by couponUser.userId
        """)
    List<UserCouponCountResponse> countByUserIds(Collection<Long> userIds);

    Optional<CouponUser> findByIdAndUserIdAndUserCouponStatus(Long id, Long userId, String userCouponStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select couponUser from CouponUser couponUser where couponUser.id = :id and couponUser.userId = :userId")
    Optional<CouponUser> lockByIdAndUserId(Long id, Long userId);
}
