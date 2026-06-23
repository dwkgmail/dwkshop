package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminAccountResponse;
import com.dwkshop.backend.admin.dto.AdminAssignRoleRequest;
import com.dwkshop.backend.admin.dto.AdminCouponRequest;
import com.dwkshop.backend.admin.dto.AdminCouponResponse;
import com.dwkshop.backend.admin.dto.AdminOperationLogResponse;
import com.dwkshop.backend.admin.dto.AdminRoleResponse;
import com.dwkshop.backend.admin.dto.AdminStatusRequest;
import com.dwkshop.backend.admin.dto.AdminUserResponse;
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
    private final AdminOperationLogService operationLogService;

    public AdminManagementController(AdminManagementService adminManagementService, AdminOperationLogService operationLogService) {
        this.adminManagementService = adminManagementService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminManagementService.listUsers();
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse updateUserStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        return adminManagementService.updateUserStatus(id, request);
    }

    @GetMapping("/coupons")
    public List<AdminCouponResponse> coupons() {
        return adminManagementService.listCoupons();
    }

    @PostMapping("/coupons")
    public AdminCouponResponse createCoupon(@Valid @RequestBody AdminCouponRequest request) {
        return adminManagementService.createCoupon(request);
    }

    @PatchMapping("/coupons/{id}/status")
    public AdminCouponResponse updateCouponStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        return adminManagementService.updateCouponStatus(id, request);
    }

    @GetMapping("/roles")
    public List<AdminRoleResponse> roles() {
        return adminManagementService.listRoles();
    }

    @GetMapping("/admin-users")
    public List<AdminAccountResponse> adminUsers() {
        return adminManagementService.listAdminAccounts();
    }

    @PatchMapping("/admin-users/{id}/role")
    public AdminAccountResponse assignRole(@PathVariable Long id, @Valid @RequestBody AdminAssignRoleRequest request) {
        return adminManagementService.assignRole(id, request);
    }

    @PatchMapping("/admin-users/{id}/status")
    public AdminAccountResponse updateAdminStatus(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        return adminManagementService.updateAdminStatus(id, request);
    }

    @GetMapping("/operation-logs")
    public List<AdminOperationLogResponse> operationLogs() {
        return operationLogService.listRecent();
    }
}
