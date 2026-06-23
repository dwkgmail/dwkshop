package com.dwkshop.backend.admin.dto;

public record AdminRoleResponse(
    Long id,
    String roleCode,
    String roleName,
    String permissions,
    String status
) {
}
