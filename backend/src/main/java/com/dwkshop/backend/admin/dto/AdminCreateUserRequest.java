package com.dwkshop.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    String mobile,

    @NotBlank
    @Size(min = 6, max = 64)
    String password,

    @Size(max = 64)
    String nickname,

    @Pattern(regexp = "^(ACTIVE|DISABLED|ENABLED|active|disabled|enabled)$")
    String status
) {
}
