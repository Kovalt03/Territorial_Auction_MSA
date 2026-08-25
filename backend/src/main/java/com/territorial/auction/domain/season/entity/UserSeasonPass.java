package com.territorial.auction.domain.season.entity;

import com.territorial.auction.domain.season.SeasonPassPolicy;
import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "user_season_passes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSeasonPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_pass_id", nullable = false)
    private SeasonPass seasonPass;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    // 레벨 보상으로 얻은 추가 건설 시간 감소 % — 패스 기본값에 더해진다.
    @Column(nullable = false)
    private Integer bonusBuildTimeReductionPct = 0;

    @Builder
    public UserSeasonPass(
            User user, SeasonPass seasonPass, LocalDateTime startedAt, LocalDateTime expiresAt) {
        this.user = user;
        this.seasonPass = seasonPass;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void addBuildTimeReduction(int pct) {
        this.bonusBuildTimeReductionPct += pct;
    }

    // 패스 기본 감소율 + 보상으로 얻은 추가 감소율 (상한 적용)
    public int totalBuildTimeReductionPct() {
        int total = seasonPass.getBuildTimeReductionPct() + bonusBuildTimeReductionPct;
        return Math.min(total, SeasonPassPolicy.MAX_BUILD_TIME_REDUCTION_PCT);
    }
}
