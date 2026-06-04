package com.dwkshop.backend.order.dto;

public record OrderAddressResponse(
    Long id,
    String receiverName,
    String receiverMobile,
    String province,
    String city,
    String district,
    String detailAddress,
    Boolean defaultFlag
) {
}
