package com.dwkshop.backend.marketing;

import com.dwkshop.backend.marketing.dto.MarketingCouponSelectionResponse;
import com.dwkshop.backend.marketing.dto.LockCouponRequest;
import com.dwkshop.backend.marketing.dto.UseCouponRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/marketing")
public class MarketingInternalController {

    private final MarketingService marketingService;

    public MarketingInternalController(MarketingService marketingService) {
        this.marketingService = marketingService;
    }

    @GetMapping("/users/{userId}/coupons/selection")
    public MarketingCouponSelectionResponse selectCoupon(
        @PathVariable Long userId,
        @RequestParam(required = false) Long requestedCouponUserId,
        @RequestParam int productAmount
    ) {
        return marketingService.selectCoupon(userId, requestedCouponUserId, productAmount);
    }

    @PostMapping("/users/{userId}/coupons/{userCouponId}/lock")
    public void lockCoupon(
        @PathVariable Long userId,
        @PathVariable Long userCouponId,
        @Valid @RequestBody LockCouponRequest request
    ) {
        marketingService.lockCoupon(userId, userCouponId, request.lockKey(), request.productAmount());
    }

    @PostMapping("/users/{userId}/coupons/{userCouponId}/use")
    public void useCoupon(
        @PathVariable Long userId,
        @PathVariable Long userCouponId,
        @Valid @RequestBody UseCouponRequest request
    ) {
        marketingService.useCoupon(userId, userCouponId, request.orderId());
    }

    @PostMapping("/users/{userId}/coupons/{userCouponId}/release")
    public void releaseCoupon(
        @PathVariable Long userId,
        @PathVariable Long userCouponId,
        @Valid @RequestBody UseCouponRequest request
    ) {
        marketingService.releaseCoupon(userId, userCouponId, request.orderId());
    }

    @PostMapping("/users/{userId}/coupons/{userCouponId}/refund")
    public void refundCoupon(
        @PathVariable Long userId,
        @PathVariable Long userCouponId,
        @Valid @RequestBody UseCouponRequest request
    ) {
        marketingService.refundCoupon(userId, userCouponId, request.orderId());
    }
}
