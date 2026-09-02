package com.territorial.combat.domain.military.entity;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "siege_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiegeEvent {

    public enum SiegeStatus {
        PENDING,
        RESOLVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attacker_id", nullable = false)
    private Long attackerId;

    @Column(name = "defender_id", nullable = false)
    private Long defenderId;

    @Column(name = "target_territory_id", nullable = false)
    private Long targetTerritoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_building_id")
    private BuildingInstance targetBuilding;

    @Column(nullable = false)
    private Integer attackZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SiegeStatus status;

    @Column(nullable = false)
    private LocalDateTime siegeStartAt;

    @Column(nullable = false)
    private LocalDateTime resolveAt;

    @Builder
    public SiegeEvent(
            Long attackerId,
            Long defenderId,
            Long targetTerritoryId,
            BuildingInstance targetBuilding,
            Integer attackZone,
            LocalDateTime siegeStartAt,
            LocalDateTime resolveAt) {
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.targetTerritoryId = targetTerritoryId;
        this.targetBuilding = targetBuilding;
        this.attackZone = attackZone;
        this.status = SiegeStatus.PENDING;
        this.siegeStartAt = siegeStartAt;
        this.resolveAt = resolveAt;
    }

    public void resolve() {
        status = SiegeStatus.RESOLVED;
    }
}
