package com.dwkshop.backend.order.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminShipOrderRequest(
    @NotBlank(message = "物流公司不能为空")
    String logisticsCompany,
    @NotBlank(message = "物流单号不能为空")
    String logisticsNo,
    String deliveryRemark
) {
}
