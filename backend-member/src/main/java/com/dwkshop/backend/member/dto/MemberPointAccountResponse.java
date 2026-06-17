package com.dwkshop.backend.member.dto;

public record MemberPointAccountResponse(
    Long userId,
    Integer availablePoints
) {
}
