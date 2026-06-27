package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.AdminRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {

    Optional<AdminRole> findByRoleCode(String roleCode);
}
