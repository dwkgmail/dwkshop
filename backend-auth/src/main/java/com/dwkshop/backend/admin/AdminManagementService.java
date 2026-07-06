package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminAccountResponse;
import com.dwkshop.backend.admin.dto.AdminAssignRoleRequest;
import com.dwkshop.backend.admin.dto.AdminCreateUserRequest;
import com.dwkshop.backend.admin.dto.AdminRoleResponse;
import com.dwkshop.backend.admin.dto.AdminStatusRequest;
import com.dwkshop.backend.admin.dto.AdminUserResponse;
import com.dwkshop.backend.audit.AdminOperationLogService;
import com.dwkshop.backend.auth.PasswordHasher;
import com.dwkshop.backend.domain.entity.AdminRole;
import com.dwkshop.backend.domain.entity.AdminUser;
import com.dwkshop.backend.domain.entity.AdminUserRole;
import com.dwkshop.backend.domain.entity.User;
import com.dwkshop.backend.domain.repository.AdminRoleRepository;
import com.dwkshop.backend.domain.repository.AdminUserRepository;
import com.dwkshop.backend.domain.repository.AdminUserRoleRepository;
import com.dwkshop.backend.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminManagementService {

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final AdminOperationLogService operationLogService;
    private final PasswordHasher passwordHasher;
    private final AdminUserStatsClient statsClient;

    public AdminManagementService(
        UserRepository userRepository,
        AdminUserRepository adminUserRepository,
        AdminRoleRepository adminRoleRepository,
        AdminUserRoleRepository adminUserRoleRepository,
        AdminOperationLogService operationLogService,
        PasswordHasher passwordHasher,
        AdminUserStatsClient statsClient
    ) {
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.adminUserRoleRepository = adminUserRoleRepository;
        this.operationLogService = operationLogService;
        this.passwordHasher = passwordHasher;
        this.statsClient = statsClient;
    }

    @Transactional
    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        String mobile = request.mobile().trim();
        if (userRepository.existsByMobile(mobile)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile already exists");
        }
        User user = new User();
        user.setId(nextUserId());
        user.setMobile(mobile);
        user.setNickname(resolveNickname(request.nickname(), mobile));
        user.setPasswordHash(passwordHasher.hash(request.password().trim()));
        user.setStatus(request.status() == null || request.status().isBlank() ? "ACTIVE" : normalizeStatus(request.status()));
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        User saved = userRepository.save(user);
        operationLogService.record(
            "USER_CREATE",
            "USER",
            saved.getId(),
            null,
            snapshot("mobile", saved.getMobile()),
            "Create user"
        );
        return toUserResponse(saved, statsClient.fetchStats(List.of(saved.getId())).getOrDefault(saved.getId(), AdminUserStats.empty()));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        List<User> users = userRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
            .toList();
        Map<Long, AdminUserStats> stats = statsClient.fetchStats(users.stream().map(User::getId).toList());
        return users.stream()
            .map(user -> toUserResponse(user, stats.getOrDefault(user.getId(), AdminUserStats.empty())))
            .toList();
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long id, AdminStatusRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String beforeStatus = user.getStatus();
        user.setStatus(normalizeStatus(request.status()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        operationLogService.record(
            "USER_STATUS_UPDATE",
            "USER",
            id,
            snapshot("status", beforeStatus),
            snapshot("status", user.getStatus()),
            "Update user status"
        );
        return toUserResponse(user, statsClient.fetchStats(List.of(user.getId())).getOrDefault(user.getId(), AdminUserStats.empty()));
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResponse> listRoles() {
        return adminRoleRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
            .map(this::toRoleResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAdminAccounts() {
        Map<Long, AdminRole> roles = adminRoleRepository.findAll().stream()
            .collect(Collectors.toMap(AdminRole::getId, Function.identity()));
        return adminUserRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
            .map(admin -> toAdminAccountResponse(admin, roles))
            .toList();
    }

    @Transactional
    public AdminAccountResponse assignRole(Long adminUserId, AdminAssignRoleRequest request) {
        AdminUser admin = adminUserRepository.findById(adminUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        AdminRole role = adminRoleRepository.findById(request.roleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        AdminUserRole relation = adminUserRoleRepository.findFirstByAdminUserId(adminUserId).orElse(null);
        Long beforeRoleId = relation == null ? null : relation.getRoleId();
        if (relation == null) {
            relation = new AdminUserRole();
            relation.setAdminUserId(adminUserId);
            relation.setCreatedAt(LocalDateTime.now());
        }
        relation.setRoleId(role.getId());
        adminUserRoleRepository.save(relation);
        operationLogService.record(
            "ADMIN_ROLE_ASSIGN",
            "ADMIN_USER",
            adminUserId,
            snapshot("roleId", beforeRoleId),
            snapshot("roleId", role.getId()),
            "Assign admin role"
        );
        return toAdminAccountResponse(admin, relation, Map.of(role.getId(), role));
    }

    @Transactional
    public AdminAccountResponse updateAdminStatus(Long adminUserId, AdminStatusRequest request) {
        AdminUser admin = adminUserRepository.findById(adminUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        String beforeStatus = admin.getStatus();
        admin.setStatus(normalizeStatus(request.status()));
        admin.setUpdatedAt(LocalDateTime.now());
        adminUserRepository.save(admin);
        operationLogService.record(
            "ADMIN_STATUS_UPDATE",
            "ADMIN_USER",
            adminUserId,
            snapshot("status", beforeStatus),
            snapshot("status", admin.getStatus()),
            "Update admin status"
        );
        Map<Long, AdminRole> roles = adminRoleRepository.findAll().stream()
            .collect(Collectors.toMap(AdminRole::getId, Function.identity()));
        return toAdminAccountResponse(admin, roles);
    }

    private AdminUserResponse toUserResponse(User user, AdminUserStats stats) {
        return new AdminUserResponse(
            user.getId(),
            user.getMobile(),
            user.getNickname(),
            user.getStatus(),
            stats.availablePoints(),
            stats.lockedPoints(),
            stats.orderCount(),
            stats.couponCount(),
            user.getCreatedAt()
        );
    }

    private AdminRoleResponse toRoleResponse(AdminRole role) {
        return new AdminRoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getPermissions(), role.getStatus());
    }

    private AdminAccountResponse toAdminAccountResponse(AdminUser admin, Map<Long, AdminRole> roles) {
        AdminUserRole relation = adminUserRoleRepository.findFirstByAdminUserId(admin.getId()).orElse(null);
        return toAdminAccountResponse(admin, relation, roles);
    }

    private AdminAccountResponse toAdminAccountResponse(AdminUser admin, AdminUserRole relation, Map<Long, AdminRole> roles) {
        AdminRole role = relation == null ? null : roles.get(relation.getRoleId());
        return new AdminAccountResponse(
            admin.getId(),
            admin.getUsername(),
            admin.getDisplayName(),
            admin.getStatus(),
            role == null ? null : role.getId(),
            role == null ? null : role.getRoleName(),
            admin.getCreatedAt()
        );
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ACTIVE", "DISABLED", "ENABLED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }
        return normalized;
    }

    private String resolveNickname(String nickname, String mobile) {
        return nickname == null || nickname.isBlank() ? "user" + mobile.substring(7) : nickname.trim();
    }

    private Long nextUserId() {
        return userRepository.findTopByOrderByIdDesc()
            .map(User::getId)
            .orElse(0L) + 1;
    }

    private Map<String, Object> snapshot(String key, Object value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(key, value);
        return snapshot;
    }
}
