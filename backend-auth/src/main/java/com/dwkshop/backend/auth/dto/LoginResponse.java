package com.dwkshop.backend.auth.dto;

public record LoginResponse(
    String token,
    String refreshToken,
    String tokenType,
    long expiresIn,
    Long id,
    String name,
    String role
) {
}
