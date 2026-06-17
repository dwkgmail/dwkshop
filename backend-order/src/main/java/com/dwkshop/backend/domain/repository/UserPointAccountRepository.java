package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.UserPointAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPointAccountRepository extends JpaRepository<UserPointAccount, Long> {

    Optional<UserPointAccount> findByUserId(Long userId);
}
