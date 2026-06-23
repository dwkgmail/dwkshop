package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AdminUserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRoleRepository extends JpaRepository<AdminUserRole, Long> {

    List<AdminUserRole> findByAdminUserId(Long adminUserId);

    Optional<AdminUserRole> findFirstByAdminUserId(Long adminUserId);

    void deleteByAdminUserId(Long adminUserId);
}
