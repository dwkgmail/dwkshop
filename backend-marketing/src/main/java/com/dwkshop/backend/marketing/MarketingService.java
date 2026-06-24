package com.dwkshop.backend.marketing;

import com.dwkshop.backend.domain.entity.Coupon;
import com.dwkshop.backend.domain.entity.CouponUser;
import com.dwkshop.backend.domain.repository.CouponRepository;
import com.dwkshop.backend.domain.repository.CouponUserRepository;
import com.dwkshop.backend.marketing.dto.MarketingCouponResponse;
import com.dwkshop.backend.marketing.dto.MarketingCouponSelectionResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarketingService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String LOCKED = "LOCKED";
    private static final String USED = "USED";
    private static final String RELEASED = "RELEASED";
    private static final String REFUNDED = "REFUNDED";

    private final CouponUserRepository couponUserRepository;
    private final CouponRepository couponRepository;

    public MarketingService(CouponUserRepository couponUserRepository, CouponRepository couponRepository) {
        this.couponUserRepository = couponUserRepository;
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public MarketingCouponSelectionResponse selectCoupon(Long userId, Long requestedCouponUserId, int productAmount) {
        List<CouponUser> userCoupons = couponUserRepository.findByUserIdAndUserCouponStatusIn(userId, List.of(AVAILABLE, RELEASED));
        Map<Long, Coupon> couponMap = couponRepository.findAllById(userCoupons.stream().map(CouponUser::getCouponId).toList()).stream()
            .collect(Collectors.toMap(Coupon::getId, coupon -> coupon));

        List<CouponCandidate> candidates = userCoupons.stream()
            .map(userCoupon -> new CouponCandidate(userCoupon, couponMap.get(userCoupon.getCouponId())))
            .filter(candidate -> candidate.coupon() != null)
            .filter(candidate -> isUsable(candidate.coupon(), productAmount))
            .toList();

        CouponCandidate selected;
        if (requestedCouponUserId != null) {
            selected = candidates.stream()
                .filter(candidate -> candidate.userCoupon().getId().equals(requestedCouponUserId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用"));
        } else {
            selected = candidates.stream()
                .max(Comparator
                    .comparing((CouponCandidate candidate) -> candidate.coupon().getDiscountAmount())
                    .thenComparing(candidate -> candidate.coupon().getUseEndTime(), Comparator.reverseOrder()))
                .orElse(null);
        }

        CouponCandidate finalSelected = selected;
        List<MarketingCouponResponse> responses = candidates.stream()
            .map(candidate -> toCouponResponse(candidate.userCoupon(), candidate.coupon(), finalSelected != null && finalSelected.userCoupon().getId().equals(candidate.userCoupon().getId())))
            .toList();
        return new MarketingCouponSelectionResponse(
            selected == null ? null : selected.userCoupon().getId(),
            selected == null ? 0 : selected.coupon().getDiscountAmount(),
            responses
        );
    }

    @Transactional
    public void lockCoupon(Long userId, Long userCouponId, String lockKey, int productAmount) {
        String normalizedLockKey = normalizeLockKey(lockKey);
        CouponUser couponUser = lockCouponUser(userId, userCouponId);
        if (LOCKED.equals(couponUser.getUserCouponStatus()) && normalizedLockKey.equals(couponUser.getLockKey())) {
            return;
        }
        if (!AVAILABLE.equals(couponUser.getUserCouponStatus()) && !RELEASED.equals(couponUser.getUserCouponStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用");
        }
        Coupon coupon = couponRepository.findById(couponUser.getCouponId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用"));
        if (!isUsable(coupon, productAmount)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用");
        }
        couponUser.setUserCouponStatus(LOCKED);
        couponUser.setLockKey(normalizedLockKey);
        couponUser.setLockedAt(LocalDateTime.now());
        couponUser.setReleasedAt(null);
        couponUserRepository.save(couponUser);
    }

    @Transactional
    public void useCoupon(Long userId, Long userCouponId, Long orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单 ID 不能为空");
        }
        CouponUser couponUser = lockCouponUser(userId, userCouponId);
        if (USED.equals(couponUser.getUserCouponStatus()) && orderId.equals(couponUser.getOrderId())) {
            return;
        }
        if (!LOCKED.equals(couponUser.getUserCouponStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券尚未锁定");
        }
        couponUser.setUserCouponStatus(USED);
        couponUser.setUsedAt(LocalDateTime.now());
        couponUser.setOrderId(orderId);
        couponUserRepository.save(couponUser);
    }

    @Transactional
    public void releaseCoupon(Long userId, Long userCouponId, Long orderId) {
        CouponUser couponUser = lockCouponUser(userId, userCouponId);
        if (AVAILABLE.equals(couponUser.getUserCouponStatus()) || RELEASED.equals(couponUser.getUserCouponStatus())) {
            return;
        }
        if (!LOCKED.equals(couponUser.getUserCouponStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券状态不允许释放");
        }
        couponUser.setUserCouponStatus(RELEASED);
        couponUser.setReleasedAt(LocalDateTime.now());
        couponUser.setOrderId(orderId);
        couponUserRepository.save(couponUser);
    }

    @Transactional
    public void refundCoupon(Long userId, Long userCouponId, Long orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单 ID 不能为空");
        }
        CouponUser couponUser = lockCouponUser(userId, userCouponId);
        if (REFUNDED.equals(couponUser.getUserCouponStatus()) && orderId.equals(couponUser.getOrderId())) {
            return;
        }
        if (!USED.equals(couponUser.getUserCouponStatus()) || !orderId.equals(couponUser.getOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券状态不允许退回");
        }
        couponUser.setUserCouponStatus(REFUNDED);
        couponUser.setRefundedAt(LocalDateTime.now());
        couponUserRepository.save(couponUser);
    }

    private CouponUser lockCouponUser(Long userId, Long userCouponId) {
        return couponUserRepository.lockByIdAndUserId(userCouponId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用"));
    }

    private boolean isUsable(Coupon coupon, int productAmount) {
        LocalDateTime now = LocalDateTime.now();
        return "ENABLED".equals(coupon.getCouponStatus())
            && productAmount >= coupon.getThresholdAmount()
            && !now.isBefore(coupon.getUseStartTime())
            && !now.isAfter(coupon.getUseEndTime());
    }

    private String normalizeLockKey(String lockKey) {
        String trimmed = lockKey == null ? "" : lockKey.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券锁定标识不能为空");
        }
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }

    private MarketingCouponResponse toCouponResponse(CouponUser userCoupon, Coupon coupon, boolean selected) {
        return new MarketingCouponResponse(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getCouponType(),
            coupon.getThresholdAmount(),
            coupon.getDiscountAmount(),
            selected
        );
    }

    private record CouponCandidate(CouponUser userCoupon, Coupon coupon) {
    }
}
