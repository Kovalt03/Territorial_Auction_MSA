package com.territorial.auction.domain.military.entity;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.user.entity.User;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_id", nullable = false)
    private User attacker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defender_id", nullable = false)
    private User defender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_territory_id", nullable = false)
    private Territory targetTerritory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_building_id")
    private BuildingInstance targetBuilding;

    @Column(nullable = false)
    private Integer attackZone; // 1 / 2 / 3

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SiegeStatus status;

    @Column(nullable = false)
    private LocalDateTime siegeStartAt;

    @Column(nullable = false)
    private LocalDateTime resolveAt;

    @Builder
    public SiegeEvent(
            User attacker,
            User defender,
            Territory targetTerritory,
            BuildingInstance targetBuilding,
            Integer attackZone,
            LocalDateTime siegeStartAt,
            LocalDateTime resolveAt) {
        this.attacker = attacker;
        this.defender = defender;
        this.targetTerritory = targetTerritory;
        this.targetBuilding = targetBuilding;
        this.attackZone = attackZone;
        this.status = SiegeStatus.PENDING;
        this.siegeStartAt = siegeStartAt;
        this.resolveAt = resolveAt;
    }

    public void resolve() {
        this.status = SiegeStatus.RESOLVED;
    }
}
