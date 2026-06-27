package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AdminUserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRoleRepository extends JpaRepository<AdminUserRole, Long> {

    Optional<AdminUserRole> findFirstByAdminUserId(Long adminUserId);
}
