package com.territorial.auction.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "unit_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name; // INFANTRY / ARCHER / KNIGHT — 서버 식별자, 변경 불가

    @Column(length = 30)
    private String displayName; // 한글 표시명. NULL이면 프론트 기본 매핑 사용

    @Column(length = 10)
    private String icon; // 이모지 아이콘

    @Column(length = 7)
    private String colorHex; // #RRGGBB

    @Column(nullable = false)
    private Integer attackPower;

    @Column(nullable = false)
    private Integer defensePower;

    @Column(nullable = false)
    private Integer costGp;

    @Column(nullable = false)
    private Integer foodCost;

    /** 성공한 공성에서 건물 HP를 깎는 양. 교전(공격/방어)과 분리된 성벽 돌파력. */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer buildingDamage = 0;

    /** 생산에 필요한 최소 병영 레벨 */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer level = 1;

    @Builder
    public UnitType(
            String name,
            String displayName,
            String icon,
            String colorHex,
            Integer attackPower,
            Integer defensePower,
            Integer costGp,
            Integer foodCost,
            Integer buildingDamage,
            Integer level) {
        this.name = name;
        this.displayName = displayName;
        this.icon = icon;
        this.colorHex = colorHex;
        this.attackPower = attackPower;
        this.defensePower = defensePower;
        this.costGp = costGp;
        this.foodCost = foodCost;
        this.buildingDamage = buildingDamage != null ? buildingDamage : 0;
        this.level = level != null ? level : 1;
    }

    // 관리자 편집: 이름(코드)은 서버 식별자라 변경 불가.
    public void update(
            String displayName,
            String icon,
            String colorHex,
            Integer attackPower,
            Integer defensePower,
            Integer costGp,
            Integer foodCost,
            Integer buildingDamage,
            Integer level) {
        this.displayName = displayName;
        this.icon = icon;
        this.colorHex = colorHex;
        this.attackPower = attackPower;
        this.defensePower = defensePower;
        this.costGp = costGp;
        this.foodCost = foodCost;
        this.buildingDamage = buildingDamage != null ? buildingDamage : 0;
        this.level = level;
    }
}
