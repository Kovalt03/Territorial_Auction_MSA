package com.territorial.combat.domain.military.entity;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.HomeIsland;
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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "home_territory_id")
    private Long homeTerritoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_island_id")
    private HomeIsland homeIsland;

    @Column(name = "deployed_territory_id")
    private Long deployedTerritoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployed_building_id")
    private BuildingInstance deployedBuilding;

    private LocalDateTime moveCompleteAt;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer level = 1;

    @Builder
    public UnitInstance(
            Long userId,
            UnitType unitType,
            Integer quantity,
            Integer level,
            Long homeTerritoryId,
            HomeIsland homeIsland,
            LocalDateTime moveCompleteAt) {
        this.userId = userId;
        this.unitType = unitType;
        this.quantity = quantity;
        this.level = level != null ? level : 1;
        this.homeTerritoryId = homeTerritoryId;
        this.homeIsland = homeIsland;
        this.moveCompleteAt = moveCompleteAt;
    }

    public boolean isInTransit() {
        return moveCompleteAt != null;
    }

    public void finishMove() {
        moveCompleteAt = null;
    }

    public void assignHomeTerritory(Long territoryId) {
        homeTerritoryId = territoryId;
        homeIsland = null;
    }

    public void assignHomeIsland(HomeIsland island) {
        homeIsland = island;
        homeTerritoryId = null;
    }

    public void addQuantity(int amount) {
        quantity += amount;
    }

    public void subtractQuantity(int amount) {
        quantity -= amount;
    }

    public void deployTo(Long territoryId, BuildingInstance building) {
        deployedTerritoryId = territoryId;
        deployedBuilding = building;
    }

    public void recall() {
        deployedTerritoryId = null;
        deployedBuilding = null;
    }
}
