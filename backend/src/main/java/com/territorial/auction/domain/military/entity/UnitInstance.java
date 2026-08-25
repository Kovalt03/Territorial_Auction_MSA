package com.territorial.auction.domain.military.entity;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "unit_instances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private Integer quantity;

    // 유닛이 귀속된 위치. 영토 또는 섬 중 하나만 설정된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_territory_id")
    private Territory homeTerritory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_island_id")
    private HomeIsland homeIsland;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployed_territory_id")
    private Territory deployedTerritory; // NULL이면 대기 중

    // 방어 배치 시 주둔한 건물. 배치된 유닛만 값이 있으며 그 건물이 속한 Zone에서 방어에 참여한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployed_building_id")
    private BuildingInstance deployedBuilding;

    // 위치 간 이동 중이면 도착 예정 시각. NULL이면 이동 중 아님(대기/배치 상태).
    // 이동 중 유닛은 귀속지가 이미 도착지로 설정돼 있으나 도착 전까지 방어·배치·재이동 불가.
    @Column private LocalDateTime moveCompleteAt;

    /** 유닛 레벨(연구로 해금해 생산 시 선택). 레벨별 스탯은 UnitTypeLevelSpec, 레벨 1은 UnitType 기본 스탯. */
    // 기존 행이 있는 테이블에 ddl-auto가 NOT NULL 컬럼을 추가할 수 있도록 DB 기본값 명시.
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer level = 1;

    @Builder
    public UnitInstance(
            User user,
            UnitType unitType,
            Integer quantity,
            Integer level,
            Territory homeTerritory,
            HomeIsland homeIsland,
            LocalDateTime moveCompleteAt) {
        this.user = user;
        this.unitType = unitType;
        this.quantity = quantity;
        this.level = level != null ? level : 1;
        this.homeTerritory = homeTerritory;
        this.homeIsland = homeIsland;
        this.moveCompleteAt = moveCompleteAt;
    }

    public boolean isInTransit() {
        return this.moveCompleteAt != null;
    }

    /** 이동 완료 처리 — 이동 중 표시를 지운다. */
    public void finishMove() {
        this.moveCompleteAt = null;
    }

    /** 귀속 위치 설정 — 영토와 섬은 배타적이다. */
    public void assignHomeTerritory(Territory territory) {
        this.homeTerritory = territory;
        this.homeIsland = null;
    }

    public void assignHomeIsland(HomeIsland island) {
        this.homeIsland = island;
        this.homeTerritory = null;
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void subtractQuantity(int amount) {
        this.quantity -= amount;
    }

    public void deployTo(Territory territory, BuildingInstance building) {
        this.deployedTerritory = territory;
        this.deployedBuilding = building;
    }

    public void recall() {
        this.deployedTerritory = null;
        this.deployedBuilding = null;
    }
}
