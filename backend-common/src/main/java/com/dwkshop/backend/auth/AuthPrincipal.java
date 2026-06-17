package com.dwkshop.backend.auth;

public record AuthPrincipal(Long id, String subject, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isUser() {
        return "USER".equals(role);
    }
}
