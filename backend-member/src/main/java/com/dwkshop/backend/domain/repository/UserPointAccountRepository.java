package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.UserPointAccount;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface UserPointAccountRepository extends JpaRepository<UserPointAccount, Long> {

    Optional<UserPointAccount> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserPointAccount account where account.userId = :userId")
    Optional<UserPointAccount> findLockedByUserId(@Param("userId") Long userId);
}
