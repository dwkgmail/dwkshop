package com.dwkshop.backend.member.dto;

public record MemberAddressResponse(
    Long id,
    Long userId,
    String receiverName,
    String receiverMobile,
    String province,
    String city,
    String district,
    String detailAddress,
    Boolean defaultFlag
) {
}
