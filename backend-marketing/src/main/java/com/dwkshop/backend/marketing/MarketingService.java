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

    private final CouponUserRepository couponUserRepository;
    private final CouponRepository couponRepository;

    public MarketingService(CouponUserRepository couponUserRepository, CouponRepository couponRepository) {
        this.couponUserRepository = couponUserRepository;
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public MarketingCouponSelectionResponse selectCoupon(Long userId, Long requestedCouponUserId, int productAmount) {
        List<CouponUser> userCoupons = couponUserRepository.findByUserIdAndUserCouponStatus(userId, "UNUSED");
        Map<Long, Coupon> couponMap = couponRepository.findAllById(userCoupons.stream().map(CouponUser::getCouponId).toList()).stream()
            .collect(Collectors.toMap(Coupon::getId, coupon -> coupon));

        List<CouponCandidate> candidates = userCoupons.stream()
            .map(userCoupon -> new CouponCandidate(userCoupon, couponMap.get(userCoupon.getCouponId())))
            .filter(candidate -> candidate.coupon() != null)
            .filter(candidate -> "ENABLED".equals(candidate.coupon().getCouponStatus()))
            .filter(candidate -> productAmount >= candidate.coupon().getThresholdAmount())
            .filter(candidate -> {
                LocalDateTime now = LocalDateTime.now();
                return !now.isBefore(candidate.coupon().getUseStartTime()) && !now.isAfter(candidate.coupon().getUseEndTime());
            })
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
    public void useCoupon(Long userId, Long userCouponId, Long orderId) {
        CouponUser couponUser = couponUserRepository.findByIdAndUserIdAndUserCouponStatus(userCouponId, userId, "UNUSED")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用"));
        couponUser.setUserCouponStatus("USED");
        couponUser.setUsedAt(LocalDateTime.now());
        couponUser.setOrderId(orderId);
        couponUserRepository.save(couponUser);
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
