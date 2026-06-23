package com.dwkshop.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminAssignRoleRequest(@NotNull Long roleId) {
}
