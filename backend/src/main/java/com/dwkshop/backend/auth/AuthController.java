package com.dwkshop.backend.auth;

import com.dwkshop.backend.auth.dto.AdminLoginRequest;
import com.dwkshop.backend.auth.dto.ChangePasswordRequest;
import com.dwkshop.backend.auth.dto.LoginResponse;
import com.dwkshop.backend.auth.dto.RefreshTokenRequest;
import com.dwkshop.backend.auth.dto.UserLoginRequest;
import com.dwkshop.backend.auth.dto.UserRegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/admin/auth/login")
    public LoginResponse loginAdmin(@Valid @RequestBody AdminLoginRequest request) {
        return authService.loginAdmin(request.username(), request.password());
    }

    @PostMapping("/admin/auth/refresh")
    public LoginResponse refreshAdmin(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken(), "ADMIN");
    }

    @PostMapping("/admin/auth/change-password")
    public LoginResponse changeAdminPassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changeAdminPassword(request.oldPassword(), request.newPassword());
    }

    @PostMapping("/admin/auth/logout")
    public ResponseEntity<Void> logoutAdmin() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    public LoginResponse loginUser(@Valid @RequestBody UserLoginRequest request) {
        return authService.loginUser(request.mobile(), request.password());
    }

    @PostMapping("/api/auth/register")
    public LoginResponse registerUser(@Valid @RequestBody UserRegisterRequest request) {
        return authService.registerUser(request.mobile(), request.password(), request.nickname());
    }

    @PostMapping("/api/auth/refresh")
    public LoginResponse refreshUser(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken(), "USER");
    }

    @PostMapping("/api/auth/change-password")
    public LoginResponse changeUserPassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changeUserPassword(request.oldPassword(), request.newPassword());
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logoutUser() {
        return ResponseEntity.noContent().build();
    }
}
