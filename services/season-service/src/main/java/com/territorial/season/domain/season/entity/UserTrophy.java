package com.territorial.season.domain.season.entity;

import com.territorial.season.domain.season.TierPolicy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_trophies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserTrophy {

    public enum League {
        BRONZE,
        SILVER,
        GOLD,
        DIAMOND,
        CHAMPION
    }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private Integer score = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private League league = League.BRONZE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column private Long lastResetSeasonId;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserTrophy(Long userId, Season season) {
        this.userId = userId;
        this.season = season;
    }

    // Demotes score by one sub-tier; idempotent within the same season
    public void applySeasonReset(Long seasonId) {
        if (seasonId.equals(this.lastResetSeasonId)) return;
        this.score = TierPolicy.calculateResetScore(this.score);
        this.league = TierPolicy.calculateLeague(this.score);
        this.lastResetSeasonId = seasonId;
    }
}
