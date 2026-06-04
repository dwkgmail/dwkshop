package com.dwkshop.backend.auth;

import com.dwkshop.backend.auth.dto.LoginResponse;
import com.dwkshop.backend.domain.entity.AdminUser;
import com.dwkshop.backend.domain.entity.User;
import com.dwkshop.backend.domain.repository.AdminUserRepository;
import com.dwkshop.backend.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthTokenService tokenService;
    private final long ttlSeconds;

    public AuthService(
        AdminUserRepository adminUserRepository,
        UserRepository userRepository,
        PasswordHasher passwordHasher,
        AuthTokenService tokenService,
        @Value("${dwkshop.auth.ttl-seconds:86400}") long ttlSeconds
    ) {
        this.adminUserRepository = adminUserRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.ttlSeconds = ttlSeconds;
    }

    public LoginResponse loginAdmin(String username, String password) {
        AdminUser admin = adminUserRepository.findByUsername(username)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("账号或密码错误"));
        if (!passwordHasher.matches(password, admin.getPasswordHash())) {
            throw new AuthException("账号或密码错误");
        }
        String token = tokenService.issue(admin.getId(), admin.getUsername(), "ADMIN");
        return new LoginResponse(token, "Bearer", ttlSeconds, admin.getId(), admin.getDisplayName(), "ADMIN");
    }

    public LoginResponse loginUser(String mobile, String password) {
        User user = userRepository.findByMobile(mobile)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("手机号或密码错误"));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthException("手机号或密码错误");
        }
        String token = tokenService.issue(user.getId(), user.getMobile(), "USER");
        return new LoginResponse(token, "Bearer", ttlSeconds, user.getId(), user.getNickname(), "USER");
    }
}
