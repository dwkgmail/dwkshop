package com.dwkshop.backend.aftersale.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AftersaleResponse(
    Long id,
    String aftersaleNo,
    Long orderId,
    String orderNo,
    Long userId,
    String receiverMobile,
    String aftersaleType,
    String refundScope,
    String aftersaleStatus,
    List<AftersaleItemResponse> refundItems,
    Integer refundAmount,
    String refundAmountText,
    Boolean includeFreight,
    String reason,
    String refundReasonType,
    List<String> evidenceImages,
    String returnLogisticsCompany,
    String returnLogisticsNo,
    String rejectReason,
    LocalDateTime applyTime,
    LocalDateTime auditTime,
    LocalDateTime refundTime
) {
}
