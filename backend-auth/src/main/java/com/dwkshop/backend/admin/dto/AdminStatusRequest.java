package com.dwkshop.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminStatusRequest(@NotBlank String status) {
}
