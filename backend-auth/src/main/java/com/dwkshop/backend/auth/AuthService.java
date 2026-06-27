package com.dwkshop.backend.auth;

import com.dwkshop.backend.auth.dto.LoginResponse;
import com.dwkshop.backend.domain.entity.AdminRole;
import com.dwkshop.backend.domain.entity.AdminUser;
import com.dwkshop.backend.domain.entity.AdminUserRole;
import com.dwkshop.backend.domain.entity.User;
import com.dwkshop.backend.domain.repository.AdminRoleRepository;
import com.dwkshop.backend.domain.repository.AdminUserRepository;
import com.dwkshop.backend.domain.repository.AdminUserRoleRepository;
import com.dwkshop.backend.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthTokenService tokenService;
    private final long ttlSeconds;

    public AuthService(
        AdminUserRepository adminUserRepository,
        AdminRoleRepository adminRoleRepository,
        AdminUserRoleRepository adminUserRoleRepository,
        UserRepository userRepository,
        PasswordHasher passwordHasher,
        AuthTokenService tokenService,
        @Value("${dwkshop.auth.ttl-seconds:86400}") long ttlSeconds
    ) {
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.adminUserRoleRepository = adminUserRoleRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.ttlSeconds = ttlSeconds;
    }

    public LoginResponse loginAdmin(String username, String password) {
        AdminUser admin = adminUserRepository.findByUsername(username)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("account or password is incorrect"));
        if (!passwordHasher.matches(password, admin.getPasswordHash())) {
            throw new AuthException("account or password is incorrect");
        }
        AdminRole role = resolveAdminRole(admin.getId());
        return toResponse(admin.getId(), admin.getUsername(), admin.getDisplayName(), role.getRoleCode(), permissions(role));
    }

    public LoginResponse loginUser(String mobile, String password) {
        User user = userRepository.findByMobile(mobile)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("mobile or password is incorrect"));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthException("mobile or password is incorrect");
        }
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER", Set.of());
    }

    @Transactional
    public LoginResponse registerUser(String mobile, String password, String nickname) {
        if (userRepository.existsByMobile(mobile)) {
            throw new AuthException("mobile already registered");
        }
        User user = new User();
        user.setId(nextUserId());
        user.setMobile(mobile);
        user.setNickname((nickname == null || nickname.isBlank()) ? "user" + mobile.substring(7) : nickname.trim());
        user.setPasswordHash(passwordHasher.hash(password));
        user.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER", Set.of());
    }

    @Transactional
    public LoginResponse changeUserPassword(String oldPassword, String newPassword) {
        AuthPrincipal principal = AuthContext.current()
            .filter(AuthPrincipal::isUser)
            .orElseThrow(() -> new AuthException("please login first"));
        User user = userRepository.findById(principal.id())
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("invalid login status"));
        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthException("old password is incorrect");
        }
        user.setPasswordHash(passwordHasher.hash(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER", Set.of());
    }

    @Transactional
    public LoginResponse changeAdminPassword(String oldPassword, String newPassword) {
        AuthPrincipal principal = AuthContext.current()
            .filter(AuthPrincipal::isAdmin)
            .orElseThrow(() -> new AuthException("admin login required"));
        AdminUser admin = adminUserRepository.findById(principal.id())
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("invalid login status"));
        if (!passwordHasher.matches(oldPassword, admin.getPasswordHash())) {
            throw new AuthException("old password is incorrect");
        }
        admin.setPasswordHash(passwordHasher.hash(newPassword));
        admin.setUpdatedAt(LocalDateTime.now());
        adminUserRepository.save(admin);
        AdminRole role = resolveAdminRole(admin.getId());
        return toResponse(admin.getId(), admin.getUsername(), admin.getDisplayName(), role.getRoleCode(), permissions(role));
    }

    public LoginResponse refresh(String refreshToken, String expectedRole) {
        AuthPrincipal principal = tokenService.verifyRefresh(refreshToken);
        if ("USER".equals(expectedRole) != principal.isUser()) {
            throw new AuthException("invalid login status");
        }
        if (principal.isAdmin()) {
            AdminUser admin = adminUserRepository.findById(principal.id())
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new AuthException("invalid login status"));
            AdminRole role = resolveAdminRole(admin.getId());
            return toResponse(admin.getId(), admin.getUsername(), admin.getDisplayName(), role.getRoleCode(), permissions(role));
        }
        User user = userRepository.findById(principal.id())
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new AuthException("invalid login status"));
        return toResponse(user.getId(), user.getMobile(), user.getNickname(), "USER", Set.of());
    }

    private LoginResponse toResponse(Long id, String subject, String name, String role, Collection<String> permissions) {
        String token = tokenService.issue(id, subject, role, permissions);
        String refreshToken = tokenService.issueRefresh(id, subject, role, permissions);
        return new LoginResponse(token, refreshToken, "Bearer", ttlSeconds, id, name, role);
    }

    private AdminRole resolveAdminRole(Long adminUserId) {
        return adminUserRoleRepository.findFirstByAdminUserId(adminUserId)
            .map(AdminUserRole::getRoleId)
            .flatMap(adminRoleRepository::findById)
            .filter(role -> "ACTIVE".equals(role.getStatus()))
            .orElseGet(() -> adminRoleRepository.findByRoleCode("SUPER_ADMIN")
                .orElseThrow(() -> new AuthException("admin role unavailable")));
    }

    private Set<String> permissions(AdminRole role) {
        if ("SUPER_ADMIN".equals(role.getRoleCode())) {
            return Set.of("*");
        }
        return Arrays.stream(role.getPermissions().split(","))
            .map(String::trim)
            .filter(permission -> !permission.isEmpty())
            .collect(Collectors.toSet());
    }

    private Long nextUserId() {
        return userRepository.findTopByOrderByIdDesc()
            .map(User::getId)
            .orElse(0L) + 1;
    }
}
