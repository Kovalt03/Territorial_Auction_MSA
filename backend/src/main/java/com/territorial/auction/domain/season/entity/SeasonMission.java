package com.territorial.auction.domain.season.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "season_missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonMission {

    /** 미션 갱신 주기. SEASON은 시즌 내 1회. */
    public enum MissionPeriod {
        DAILY,
        WEEKLY,
        SEASON
    }

    /** 진행도 증가 트리거. ATTENDANCE는 출석 버튼으로 직접 완료(self-claim). */
    public enum MissionTrigger {
        ATTENDANCE,
        AUCTION_WIN,
        SIEGE_WIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MissionPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionTrigger triggerType;

    @Column(nullable = false)
    private Integer goalCount;

    @Column(nullable = false)
    private Integer xpReward;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Builder
    public SeasonMission(
            Season season,
            String code,
            String title,
            String description,
            MissionPeriod period,
            MissionTrigger triggerType,
            Integer goalCount,
            Integer xpReward,
            Integer sortOrder) {
        this.season = season;
        this.code = code;
        this.title = title;
        this.description = description;
        this.period = period;
        this.triggerType = triggerType;
        this.goalCount = goalCount;
        this.xpReward = xpReward;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    public boolean isSelfClaim() {
        return this.triggerType == MissionTrigger.ATTENDANCE;
    }
}
