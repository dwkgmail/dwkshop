package com.dwkshop.backend.auth;

import java.util.Set;

public record AuthPrincipal(Long id, String subject, String role, Set<String> permissions) {

    public AuthPrincipal(Long id, String subject, String role) {
        this(id, subject, role, Set.of());
    }

    public boolean isAdmin() {
        return !"USER".equals(role);
    }

    public boolean isUser() {
        return "USER".equals(role);
    }

    public boolean hasPermission(String permission) {
        return isAdmin() && (permissions.contains("*") || permissions.contains(permission));
    }
}
