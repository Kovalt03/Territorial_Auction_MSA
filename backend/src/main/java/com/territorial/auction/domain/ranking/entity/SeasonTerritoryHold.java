package com.territorial.auction.domain.ranking.entity;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "season_territory_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonTerritoryHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id", nullable = false)
    private Territory territory;

    @Column(nullable = false, length = 1)
    private String grade;

    @Column(nullable = false)
    private LocalDateTime heldFrom;

    private LocalDateTime heldUntil;

    @Builder
    public SeasonTerritoryHold(
            Season season, User user, Territory territory, String grade, LocalDateTime heldFrom) {
        this.season = season;
        this.user = user;
        this.territory = territory;
        this.grade = grade;
        this.heldFrom = heldFrom;
    }

    public void closeHold(LocalDateTime heldUntil) {
        this.heldUntil = heldUntil;
    }
}
