package com.dwkshop.backend.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AdminCouponRequest(
    @NotBlank String name,
    String couponCode,
    @NotBlank String couponType,
    @NotNull @Min(0) Integer thresholdAmount,
    @NotNull @Min(0) Integer discountAmount,
    Integer discountRate,
    @NotNull @Min(1) Integer totalQuantity,
    @NotNull LocalDateTime receiveStartTime,
    @NotNull LocalDateTime receiveEndTime,
    @NotNull LocalDateTime useStartTime,
    @NotNull LocalDateTime useEndTime,
    @NotBlank String couponStatus
) {
}
