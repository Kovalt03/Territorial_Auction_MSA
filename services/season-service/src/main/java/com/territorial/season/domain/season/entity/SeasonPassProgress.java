package com.territorial.season.domain.season.entity;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.season.domain.season.SeasonPassPolicy;
import com.territorial.season.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "season_pass_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "season_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonPassProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int xp;

    @Builder
    public SeasonPassProgress(Long userId, Season season) {
        this.userId = userId;
        this.season = season;
        this.level = 1;
        this.xp = 0;
    }

    /** AP를 지불해 즉시 1레벨 상승. 잔여 XP는 초기화. */
    public void levelUpByPurchase() {
        if (this.level >= SeasonPassPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.SEASON_LEVEL_MAX_REACHED);
        }
        this.level++;
        this.xp = 0;
    }

    public void addXp(int amount, int xpPerLevel) {
        if (this.level >= SeasonPassPolicy.MAX_LEVEL) return;
        this.xp += amount;
        while (xpPerLevel > 0 && this.xp >= xpPerLevel && this.level < SeasonPassPolicy.MAX_LEVEL) {
            this.level++;
            this.xp -= xpPerLevel;
        }
        if (this.level >= SeasonPassPolicy.MAX_LEVEL) {
            this.xp = 0;
        }
    }
}
