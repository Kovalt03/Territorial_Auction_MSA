package com.territorial.admin.domain.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 관리자 신원 — admin-service가 온전히 소유(공개 유저 user-service와 분리). 게임 users 테이블의 role=ADMIN 계정에서 일회성 이관한다.
 * TOTP 시크릿도 여기 보관(더 이상 users 컬럼 아님).
 */
@Entity
@Table(name = "admin_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

    public enum Status {
        ACTIVE,
        SUSPENDED
    }

    public enum Role {
        ADMIN,
        SUPER_ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // 2FA 미등록이면 null. 최초 로그인 시 등록 유도.
    private String totpSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ADMIN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AdminAccount(String email, String passwordHash, String totpSecret, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.totpSecret = totpSecret;
        this.role = role != null ? role : Role.ADMIN;
    }

    public void enrollTotp(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isTotpEnrolled() {
        return this.totpSecret != null;
    }

    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }
}
