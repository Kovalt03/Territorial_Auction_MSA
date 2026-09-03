package com.territorial.combat.domain.building.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "combat_user_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CombatUserSnapshot {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public CombatUserSnapshot(Long userId, String nickname, String status) {
        this.userId = userId;
        this.nickname = nickname;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
