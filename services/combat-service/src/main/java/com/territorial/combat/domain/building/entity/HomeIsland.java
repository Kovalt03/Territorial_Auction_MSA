package com.territorial.combat.domain.building.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "home_islands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class HomeIsland {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer level = 1;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private Integer gridSize = 10;

    @Getter(AccessLevel.NONE)
    @Column(length = 5)
    private String grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "island_grade_id")
    private IslandGrade islandGrade;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column private LocalDateTime lastHarvestAt;

    // 생산 부스터(AP) 종료 시각. 이 시각 전까지 GP·식량 생산이 배율 적용된다. null이면 미적용.
    @Column private LocalDateTime productionBoostUntil;

    @Builder
    public HomeIsland(Long userId, Integer level, IslandGrade islandGrade) {
        this.userId = userId;
        this.level = level != null ? level : 1;
        this.islandGrade = islandGrade;
        this.gridSize = islandGrade != null ? islandGrade.getGridSize() : 10;
        this.grade = islandGrade != null ? islandGrade.getName() : "D";
        this.lastHarvestAt = LocalDateTime.now();
    }

    public int getGridSize() {
        return islandGrade != null ? islandGrade.getGridSize() : gridSize;
    }

    public String getGrade() {
        return islandGrade != null ? islandGrade.getName() : (grade != null ? grade : "D");
    }

    public int getZone1Radius() {
        return islandGrade != null ? islandGrade.getZone1Radius() : 2;
    }

    public int getZone2Radius() {
        return islandGrade != null ? islandGrade.getZone2Radius() : 4;
    }

    public void recordHarvest() {
        this.lastHarvestAt = LocalDateTime.now();
    }

    public boolean isProductionBoostActive(LocalDateTime now) {
        return productionBoostUntil != null && productionBoostUntil.isAfter(now);
    }

    // 생산 부스터를 발동한다. 이미 활성 중이면 예외 — 발동 지점(Service)에서 검증 후 호출.
    public void activateProductionBoost(LocalDateTime until) {
        this.productionBoostUntil = until;
    }

    public void upgradeIsland(IslandGrade newGrade) {
        this.islandGrade = newGrade;
        this.gridSize = newGrade.getGridSize();
        this.grade = newGrade.getName();
    }
}
