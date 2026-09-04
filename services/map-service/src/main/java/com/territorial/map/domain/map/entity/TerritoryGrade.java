package com.territorial.map.domain.map.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "territory_grades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1)
    private String grade; // S / A / B / C / D

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal productionMultiplier;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal auctionPriceMultiplier;

    @Column(nullable = false)
    private Integer preBuiltCount = 0;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal spawnRate;

    @Column(nullable = false)
    private Integer gridSize;

    // 필드명에 숫자가 섞여 있어 Hibernate 기본 전략은 zone1radius로 매핑한다 — 컬럼명을 명시해 고정.
    @Column(name = "zone1_radius", nullable = false)
    private Integer zone1Radius;

    @Column(name = "zone2_radius", nullable = false)
    private Integer zone2Radius;

    @Builder
    public TerritoryGrade(
            String grade,
            BigDecimal productionMultiplier,
            BigDecimal auctionPriceMultiplier,
            Integer preBuiltCount,
            BigDecimal spawnRate,
            Integer gridSize,
            Integer zone1Radius,
            Integer zone2Radius) {
        this.grade = grade;
        this.productionMultiplier = productionMultiplier;
        this.auctionPriceMultiplier = auctionPriceMultiplier;
        this.preBuiltCount = preBuiltCount;
        this.spawnRate = spawnRate;
        this.gridSize = gridSize;
        this.zone1Radius = zone1Radius;
        this.zone2Radius = zone2Radius;
    }

    // 시드 재적용 — 등급명은 식별자라 제외하고 나머지를 yml 값으로 맞춘다.
    public void syncFromSeed(
            BigDecimal productionMultiplier,
            BigDecimal auctionPriceMultiplier,
            Integer preBuiltCount,
            BigDecimal spawnRate,
            Integer gridSize,
            Integer zone1Radius,
            Integer zone2Radius) {
        this.productionMultiplier = productionMultiplier;
        this.auctionPriceMultiplier = auctionPriceMultiplier;
        this.preBuiltCount = preBuiltCount;
        this.spawnRate = spawnRate;
        this.gridSize = gridSize;
        this.zone1Radius = zone1Radius;
        this.zone2Radius = zone2Radius;
    }
}
