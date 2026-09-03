package com.territorial.auction.domain.season.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "season_pass_level_rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonPassLevelReward {

    public enum RewardTrack {
        FREE,
        PREMIUM
    }

    public enum RewardKind {
        ITEM,
        GP,
        BUILD_TIME_REDUCTION // quantity = 추가 감소 %
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(nullable = false)
    private Integer level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RewardTrack track = RewardTrack.FREE;

    @Column(nullable = false, length = 100)
    private String rewardName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RewardKind rewardKind = RewardKind.ITEM;

    // ITEM 보상일 때 지급할 아이템 타입. GP 보상이면 null.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RewardItemType itemType;

    // ITEM: 지급 개수, GP: 지급 GP 수량, BUILD_TIME_REDUCTION: 추가 감소 %
    @Column(nullable = false)
    private Integer quantity = 1;

    @Builder
    public SeasonPassLevelReward(
            Season season,
            Integer level,
            RewardTrack track,
            String rewardName,
            RewardKind rewardKind,
            RewardItemType itemType,
            Integer quantity) {
        this.season = season;
        this.level = level;
        this.track = track != null ? track : RewardTrack.FREE;
        this.rewardName = rewardName;
        this.rewardKind = rewardKind != null ? rewardKind : RewardKind.ITEM;
        this.itemType = itemType;
        this.quantity = quantity != null ? quantity : 1;
    }
}
