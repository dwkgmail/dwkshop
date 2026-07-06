package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminAccountResponse;
import com.dwkshop.backend.admin.dto.AdminAssignRoleRequest;
import com.dwkshop.backend.admin.dto.AdminCreateUserRequest;
import com.dwkshop.backend.admin.dto.AdminRoleResponse;
import com.dwkshop.backend.admin.dto.AdminStatusRequest;
import com.dwkshop.backend.admin.dto.AdminUserResponse;
import com.dwkshop.backend.auth.RequiresConfirmation;
import com.dwkshop.backend.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    public AdminManagementController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @GetMapping("/users")
    @RequiresPermission("user:read")
    public List<AdminUserResponse> users() {
        return adminManagementService.listUsers();
    }

    @PostMapping("/users")
    @RequiresPermission("user:write")
    public AdminUserResponse createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return adminManagementService.createUser(request);
    }

    @PatchMapping("/users/{id}/status")
    @RequiresPermission("user:write")
    @RequiresConfirmation
    public AdminUserResponse updateUserStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        return adminManagementService.updateUserStatus(id, request);
    }

    @GetMapping("/roles")
    @RequiresPermission("permission:manage")
    public List<AdminRoleResponse> roles() {
        return adminManagementService.listRoles();
    }

    @GetMapping("/admin-users")
    @RequiresPermission("permission:manage")
    public List<AdminAccountResponse> adminUsers() {
        return adminManagementService.listAdminAccounts();
    }

    @PatchMapping("/admin-users/{id}/role")
    @RequiresPermission("permission:manage")
    @RequiresConfirmation
    public AdminAccountResponse assignRole(@PathVariable Long id, @Valid @RequestBody AdminAssignRoleRequest request) {
        return adminManagementService.assignRole(id, request);
    }

    @PatchMapping("/admin-users/{id}/status")
    @RequiresPermission("permission:manage")
    @RequiresConfirmation
    public AdminAccountResponse updateAdminStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        return adminManagementService.updateAdminStatus(id, request);
    }
}
