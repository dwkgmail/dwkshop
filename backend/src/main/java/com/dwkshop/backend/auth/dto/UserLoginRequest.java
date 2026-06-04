package com.dwkshop.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
    @NotBlank String mobile,
    @NotBlank String password
) {
}
