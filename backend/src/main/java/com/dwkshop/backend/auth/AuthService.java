package com.dwkshop.backend.auth;

import com.dwkshop.backend.auth.dto.LoginResponse;
import com.dwkshop.backend.domain.entity.AdminUser;
import com.dwkshop.backend.domain.entity.User;
import com.dwkshop.backend.domain.repository.AdminUserRepository;
import com.dwkshop.backend.domain.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 后台登录只允许 ACTIVE 账号通过，并统一走密码哈希校验。
        AdminUser admin = adminUserRepository.findByUsername(username)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("账号或密码错误"));
        if (!passwordHasher.matches(password, admin.getPasswordHash())) {
            throw new AuthException("账号或密码错误");
        }
        return toResponse(admin.getId(), admin.getUsername(), admin.getDisplayName(), "ADMIN");
    }

    public LoginResponse loginUser(String mobile, String password) {
        User user = userRepository.findByMobile(mobile)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("手机号或密码错误"));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthException("手机号或密码错误");
        }
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER");
    }

    @Transactional
    public LoginResponse registerUser(String mobile, String password, String nickname) {
        if (userRepository.existsByMobile(mobile)) {
            throw new AuthException("手机号已注册");
        }
        // 当前项目未引入独立号段服务，用户 ID 通过现有最大值顺延生成。
        User user = new User();
        user.setId(nextUserId());
        user.setMobile(mobile);
        user.setNickname((nickname == null || nickname.isBlank()) ? "用户" + mobile.substring(7) : nickname.trim());
        user.setPasswordHash(passwordHasher.hash(password));
        user.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER");
    }

    @Transactional
    public LoginResponse changeUserPassword(String oldPassword, String newPassword) {
        AuthPrincipal principal = AuthContext.current()
            .filter(AuthPrincipal::isUser)
            .orElseThrow(() -> new AuthException("请先登录"));
        User user = userRepository.findById(principal.id())
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("登录状态无效，请重新登录"));
        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthException("原密码错误");
        }
        user.setPasswordHash(passwordHasher.hash(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER");
    }

    @Transactional
    public LoginResponse changeAdminPassword(String oldPassword, String newPassword) {
        AuthPrincipal principal = AuthContext.current()
            .filter(AuthPrincipal::isAdmin)
            .orElseThrow(() -> new AuthException("请先登录后台"));
        AdminUser admin = adminUserRepository.findById(principal.id())
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("登录状态无效，请重新登录"));
        if (!passwordHasher.matches(oldPassword, admin.getPasswordHash())) {
            throw new AuthException("原密码错误");
        }
        admin.setPasswordHash(passwordHasher.hash(newPassword));
        admin.setUpdatedAt(LocalDateTime.now());
        adminUserRepository.save(admin);
        return toResponse(admin.getId(), admin.getUsername(), admin.getDisplayName(), "ADMIN");
    }

    public LoginResponse refresh(String refreshToken, String expectedRole) {
        // 刷新 token 时除了验签和过期时间，还会校验调用方期望的角色类型。
        AuthPrincipal principal = tokenService.verifyRefresh(refreshToken);
        if (!expectedRole.equals(principal.role())) {
            throw new AuthException("登录状态无效，请重新登录");
        }
        if (principal.isAdmin()) {
            AdminUser admin = adminUserRepository.findById(principal.id())
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new AuthException("登录状态无效，请重新登录"));
            return toResponse(admin.getId(), admin.getUsername(), admin.getDisplayName(), "ADMIN");
        }
        User user = userRepository.findById(principal.id())
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("登录状态无效，请重新登录"));
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER");
    }

    private LoginResponse toResponse(Long id, String subject, String name, String role) {
        // 每次登录或刷新都会重新签发 access token 与 refresh token。
        String token = tokenService.issue(id, subject, role);
        String refreshToken = tokenService.issueRefresh(id, subject, role);
        return new LoginResponse(token, refreshToken, "Bearer", ttlSeconds, id, name, role);
    }

    private Long nextUserId() {
        return userRepository.findTopByOrderByIdDesc()
            .map(User::getId)
            .orElse(0L) + 1;
    }
}
