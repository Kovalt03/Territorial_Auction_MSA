package com.territorial.season.domain.season.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "season_pass_reward_claims")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SeasonPassRewardClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private SeasonPassLevelReward reward;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime claimedAt;

    @Builder
    public SeasonPassRewardClaim(Long userId, SeasonPassLevelReward reward) {
        this.userId = userId;
        this.reward = reward;
    }
}
