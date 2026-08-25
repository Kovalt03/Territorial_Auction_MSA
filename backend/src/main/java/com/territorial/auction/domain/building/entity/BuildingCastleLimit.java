package com.territorial.auction.domain.building.entity;

import jakarta.persistence.*;
import lombok.*;

/** 성 레벨별로 이 건물을 몇 개까지 지을 수 있는지. 행이 없으면 제한 없음. */
@Entity
@Table(
        name = "building_castle_limits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"building_type_id", "castle_level"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingCastleLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_type_id", nullable = false)
    private BuildingType buildingType;

    @Column(nullable = false)
    private Integer castleLevel;

    @Column(nullable = false)
    private Integer maxCount;

    @Builder
    public BuildingCastleLimit(BuildingType buildingType, Integer castleLevel, Integer maxCount) {
        this.buildingType = buildingType;
        this.castleLevel = castleLevel;
        this.maxCount = maxCount;
    }

    public void updateMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}
