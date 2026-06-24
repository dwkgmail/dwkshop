package com.dwkshop.backend.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateAftersaleRequest(
    @NotNull Long orderId,
    String aftersaleType,
    String refundScope,
    List<CreateAftersaleItemRequest> refundItems,
    Boolean includeFreight,
    @NotBlank String reason,
    String returnLogisticsCompany,
    String returnLogisticsNo,
    String refundReasonType,
    List<String> evidenceImages
) {
}
