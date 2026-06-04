package com.dwkshop.backend.product.dto;

public record CategoryResponse(
    Long id,
    Long parentId,
    String name,
    Integer level,
    Integer sortOrder,
    String status
) {
}
