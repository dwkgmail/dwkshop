package com.dwkshop.backend.auth;

import java.util.Optional;

public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static Optional<AuthPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<Long> currentUserId() {
        return current().filter(AuthPrincipal::isUser).map(AuthPrincipal::id);
    }
}
