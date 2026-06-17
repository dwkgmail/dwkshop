package com.dwkshop.backend.order.dto;

import java.util.List;

public record ConfirmOrderRequest(
    String sourceType,
    List<Long> cartItemIds,
    Long skuId,
    Integer quantity,
    Long addressId,
    Long couponUserId,
    Boolean usePoints,
    String remark
) {
}
