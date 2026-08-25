package com.territorial.auction.domain.season.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "season_rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SeasonReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(nullable = false, length = 10)
    private String league;

    @Column(nullable = false)
    private Integer gpReward;

    @Column(nullable = false)
    private Integer attackTokenNormal = 0;

    @Column(nullable = false)
    private Integer attackTokenPrecision = 0;

    @Column(length = 30)
    private String titleReward; // Champion 전용 칭호, NULL 허용

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SeasonReward(
            User user,
            Season season,
            String league,
            Integer gpReward,
            Integer attackTokenNormal,
            Integer attackTokenPrecision,
            String titleReward) {
        this.user = user;
        this.season = season;
        this.league = league;
        this.gpReward = gpReward;
        this.attackTokenNormal = attackTokenNormal != null ? attackTokenNormal : 0;
        this.attackTokenPrecision = attackTokenPrecision != null ? attackTokenPrecision : 0;
        this.titleReward = titleReward;
    }
}
