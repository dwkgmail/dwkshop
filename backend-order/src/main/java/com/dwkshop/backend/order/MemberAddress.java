package com.dwkshop.backend.order;

public record MemberAddress(
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
