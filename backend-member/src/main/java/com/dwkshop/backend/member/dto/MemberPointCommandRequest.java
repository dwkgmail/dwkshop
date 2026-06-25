package com.dwkshop.backend.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MemberPointCommandRequest(
    @NotNull Long orderId,
    @NotBlank String bizNo,
    @NotNull @Positive Integer points
) {
}
