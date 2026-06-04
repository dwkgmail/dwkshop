package com.dwkshop.backend.auth;

import com.dwkshop.backend.auth.dto.AdminLoginRequest;
import com.dwkshop.backend.auth.dto.LoginResponse;
import com.dwkshop.backend.auth.dto.UserLoginRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/api/auth/login")
    public LoginResponse loginUser(@Valid @RequestBody UserLoginRequest request) {
        return authService.loginUser(request.mobile(), request.password());
    }
}
