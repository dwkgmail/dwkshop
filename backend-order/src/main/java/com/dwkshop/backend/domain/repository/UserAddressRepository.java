package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.UserAddress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    Optional<UserAddress> findFirstByUserIdAndDefaultFlagTrue(Long userId);

    Optional<UserAddress> findFirstByUserIdOrderByIdAsc(Long userId);
}
