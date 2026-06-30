package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminAssignRoleRequest;
import com.dwkshop.backend.admin.dto.AdminStatusRequest;
import com.dwkshop.backend.audit.AdminOperationLogService;
import com.dwkshop.backend.domain.entity.AdminRole;
import com.dwkshop.backend.domain.entity.AdminUser;
import com.dwkshop.backend.domain.entity.AdminUserRole;
import com.dwkshop.backend.domain.entity.User;
import com.dwkshop.backend.domain.repository.AdminRoleRepository;
import com.dwkshop.backend.domain.repository.AdminUserRepository;
import com.dwkshop.backend.domain.repository.AdminUserRoleRepository;
import com.dwkshop.backend.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock AdminUserRepository adminUserRepository;
    @Mock AdminRoleRepository adminRoleRepository;
    @Mock AdminUserRoleRepository adminUserRoleRepository;
    @Mock AdminOperationLogService operationLogService;

    AdminManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminManagementService(
            userRepository,
            adminUserRepository,
            adminRoleRepository,
            adminUserRoleRepository,
            operationLogService
        );
    }

    @Test
    void listUsersReturnsNewestFirstWithAuthOwnedFields() {
        User older = user(1L, "13800000001", "older", "ACTIVE");
        User newer = user(2L, "13800000002", "newer", "DISABLED");
        when(userRepository.findAll()).thenReturn(List.of(older, newer));

        var result = service.listUsers();

        assertThat(result).extracting("id").containsExactly(2L, 1L);
        assertThat(result.getFirst().availablePoints()).isZero();
        assertThat(result.getFirst().orderCount()).isZero();
    }

    @Test
    void updateUserStatusPersistsAndRecordsAuditLog() {
        User user = user(1L, "13800000001", "buyer", "ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var result = service.updateUserStatus(1L, new AdminStatusRequest("disabled"));

        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(user.getUpdatedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(operationLogService).record(
            eq("USER_STATUS_UPDATE"),
            eq("USER"),
            eq(1L),
            any(),
            any(),
            eq("Update user status")
        );
    }

    @Test
    void assignRoleCreatesMissingRelationAndReturnsAssignedRole() {
        AdminUser admin = adminUser(1L, "admin", "ACTIVE");
        AdminRole role = role(2L, "OPERATOR", "Operator");
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRoleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(adminUserRoleRepository.findFirstByAdminUserId(1L)).thenReturn(Optional.empty());

        var result = service.assignRole(1L, new AdminAssignRoleRequest(2L));

        assertThat(result.roleId()).isEqualTo(2L);
        assertThat(result.roleName()).isEqualTo("Operator");
        verify(adminUserRoleRepository).save(any(AdminUserRole.class));
        verify(operationLogService).record(
            eq("ADMIN_ROLE_ASSIGN"),
            eq("ADMIN_USER"),
            eq(1L),
            any(),
            any(),
            eq("Assign admin role")
        );
    }

    private User user(Long id, String mobile, String nickname, String status) {
        User user = new User();
        user.setId(id);
        user.setMobile(mobile);
        user.setNickname(nickname);
        user.setStatus(status);
        user.setPasswordHash("hash");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private AdminUser adminUser(Long id, String username, String status) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setUsername(username);
        admin.setDisplayName(username);
        admin.setStatus(status);
        admin.setPasswordHash("hash");
        admin.setCreatedAt(LocalDateTime.now());
        return admin;
    }

    private AdminRole role(Long id, String code, String name) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setPermissions("user:read");
        role.setStatus("ACTIVE");
        return role;
    }
}
