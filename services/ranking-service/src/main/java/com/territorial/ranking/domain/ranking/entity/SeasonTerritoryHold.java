package com.territorial.ranking.domain.ranking.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 시즌별 영토 점유 구간 기록. 점유 시작~종료 시간 × 등급 가중치로 영토 보유 랭킹 점수를 낸다. season·user·territory는 모두 다른
 * 서비스(season-service·user-service·map)가 소유하므로 FK 없이 식별자만 보관한다. grade는 점유 시작 시점 값을 비정규화 저장.
 */
@Entity
@Table(name = "season_territory_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonTerritoryHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "territory_id", nullable = false)
    private Long territoryId;

    @Column(nullable = false, length = 1)
    private String grade;

    @Column(nullable = false)
    private LocalDateTime heldFrom;

    private LocalDateTime heldUntil;

    @Builder
    public SeasonTerritoryHold(
            Long seasonId, Long userId, Long territoryId, String grade, LocalDateTime heldFrom) {
        this.seasonId = seasonId;
        this.userId = userId;
        this.territoryId = territoryId;
        this.grade = grade;
        this.heldFrom = heldFrom;
    }

    public void closeHold(LocalDateTime heldUntil) {
        this.heldUntil = heldUntil;
    }
}
