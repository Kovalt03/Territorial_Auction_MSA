package com.territorial.combat.domain.military.entity;

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
    private String name;

    @Column(length = 30)
    private String displayName;

    @Column(length = 10)
    private String icon;

    @Column(length = 7)
    private String colorHex;

    @Column(nullable = false)
    private Integer attackPower;

    @Column(nullable = false)
    private Integer defensePower;

    @Column(nullable = false)
    private Integer costGp;

    @Column(nullable = false)
    private Integer foodCost;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer buildingDamage = 0;

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
