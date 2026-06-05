package com.dwkshop.backend.order.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdateDeliveryStatusRequest(
    @NotBlank(message = "配送状态不能为空")
    String deliveryStatus,
    String deliveryRemark
) {
}
