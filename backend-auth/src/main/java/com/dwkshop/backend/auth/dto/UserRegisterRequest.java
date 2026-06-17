package com.dwkshop.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    String mobile,

    @NotBlank
    @Size(min = 6, max = 64)
    String password,

    @Size(max = 64)
    String nickname
) {
}
