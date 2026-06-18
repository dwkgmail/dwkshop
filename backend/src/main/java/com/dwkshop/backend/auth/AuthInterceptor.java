package com.dwkshop.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthTokenService tokenService;
    private final InternalServiceAuthConfig internalServiceAuthConfig;

    public AuthInterceptor(AuthTokenService tokenService, InternalServiceAuthConfig internalServiceAuthConfig) {
        this.tokenService = tokenService;
        this.internalServiceAuthConfig = internalServiceAuthConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            verifyInternalRequest(request);
            return true;
        }

        String token = resolveToken(request);
        if (token != null) {
            AuthPrincipal principal = tokenService.verify(token);
            if (path.startsWith("/admin/") && requiresAdminAccess(path) && !principal.isAdmin()) {
                throw new AuthException("admin login required");
            }
            AuthContext.set(principal);
        } else if (path.startsWith("/admin/") && requiresAdminAccess(path)) {
            throw new AuthException("admin login required");
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    private boolean requiresAdminAccess(String path) {
        return !path.equals("/admin/auth/login")
            && !path.equals("/admin/auth/refresh")
            && !path.equals("/admin/auth/logout");
    }

    private void verifyInternalRequest(HttpServletRequest request) {
        String expectedSecret = internalServiceAuthConfig.internalSecret();
        String actualSecret = request.getHeader(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER);
        if (expectedSecret == null || expectedSecret.isBlank() || actualSecret == null || !expectedSecret.equals(actualSecret)) {
            throw new AuthException("internal access unauthorized");
        }
    }
}
