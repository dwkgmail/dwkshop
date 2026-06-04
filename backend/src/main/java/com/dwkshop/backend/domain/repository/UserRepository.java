package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
