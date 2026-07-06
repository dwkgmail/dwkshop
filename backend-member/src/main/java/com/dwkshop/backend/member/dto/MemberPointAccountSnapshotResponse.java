package com.dwkshop.backend.member.dto;

public record MemberPointAccountSnapshotResponse(
    Long userId,
    Integer availablePoints,
    Integer lockedPoints
) {
}
