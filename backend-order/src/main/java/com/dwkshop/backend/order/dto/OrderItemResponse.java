package com.dwkshop.backend.order.dto;

import java.util.List;

public record OrderItemResponse(
    Long id,
    Long productId,
    Long skuId,
    String productName,
    String skuName,
    String specJson,
    Long categoryId,
    String brandName,
    String productImageUrl,
    Integer salePrice,
    String salePriceText,
    Integer quantity,
    Integer payAmount,
    String payAmountText,
    Integer couponShareAmount,
    String couponShareAmountText,
    Integer pointShareAmount,
    String pointShareAmountText,
    Integer freightShareAmount,
    String freightShareAmountText,
    List<PromotionShareResponse> promotionShares,
    String deliveryType,
    Boolean supportRefund,
    Boolean supportPointDeduction,
    Integer snapshotVersion,
    Integer refundableQuantity,
    Integer refundedQuantity,
    Integer aftersaleQuantity,
    Integer refundAmount,
    String refundAmountText,
    String refundStatus
) {
}
