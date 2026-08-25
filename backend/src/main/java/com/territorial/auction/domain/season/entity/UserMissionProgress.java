package com.territorial.auction.domain.season.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "user_mission_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "mission_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMissionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private SeasonMission mission;

    @Column(nullable = false)
    private int completedCount;

    @Column(nullable = false)
    private boolean claimed;

    /** 현재 진행 주기의 시작 시각. 주기가 바뀌면 reset() 으로 초기화. */
    @Column(nullable = false)
    private LocalDateTime periodStartedAt;

    @Builder
    public UserMissionProgress(User user, SeasonMission mission, LocalDateTime periodStartedAt) {
        this.user = user;
        this.mission = mission;
        this.completedCount = 0;
        this.claimed = false;
        this.periodStartedAt = periodStartedAt;
    }

    public void increment(int amount) {
        if (this.claimed) return;
        this.completedCount += amount;
    }

    public void markClaimed() {
        this.claimed = true;
    }

    /** 출석형(self-claim) 미션: 클릭 시 목표 채우고 수령 처리. */
    public void completeAndClaim(int goalCount) {
        this.completedCount = goalCount;
        this.claimed = true;
    }

    public void reset(LocalDateTime now) {
        this.completedCount = 0;
        this.claimed = false;
        this.periodStartedAt = now;
    }
}
