package com.dwkshop.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "`user`")
public class User {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String mobile;

    @Column(nullable = false, length = 64)
    private String nickname;

    private String avatarUrl;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
