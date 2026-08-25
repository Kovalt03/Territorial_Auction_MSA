package com.territorial.auction.domain.building.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "island_grades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IslandGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 5)
    private String name;

    @Column(nullable = false)
    private Integer gridSize;

    // 필드명에 숫자가 섞여 있어 Hibernate 기본 전략은 zone1radius로 매핑한다 — 컬럼명을 명시해 고정.
    @Column(name = "zone1_radius", nullable = false)
    private Integer zone1Radius;

    @Column(name = "zone2_radius", nullable = false)
    private Integer zone2Radius;

    @Column(nullable = false)
    private Integer castleLevelRequired;

    @Builder
    public IslandGrade(
            String name,
            Integer gridSize,
            Integer zone1Radius,
            Integer zone2Radius,
            Integer castleLevelRequired) {
        this.name = name;
        this.gridSize = gridSize;
        this.zone1Radius = zone1Radius;
        this.zone2Radius = zone2Radius;
        this.castleLevelRequired = castleLevelRequired;
    }

    // 시드 재적용 — 이름은 식별자라 제외하고 나머지를 yml 값으로 맞춘다.
    public void syncFromSeed(
            Integer gridSize,
            Integer zone1Radius,
            Integer zone2Radius,
            Integer castleLevelRequired) {
        this.gridSize = gridSize;
        this.zone1Radius = zone1Radius;
        this.zone2Radius = zone2Radius;
        this.castleLevelRequired = castleLevelRequired;
    }
}
