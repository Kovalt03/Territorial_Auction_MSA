package com.territorial.auction.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

/** 한 공성전에 공격자가 커밋한 병력(유닛 타입별 수량). 선언 시 대기 풀에서 차감돼 여기 기록되고, 판정 후 생존분은 환원된다. */
@Entity
@Table(name = "siege_forces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiegeForce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siege_id", nullable = false)
    private SiegeEvent siege;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private Integer quantity;

    /** 커밋된 병력의 유닛 레벨 — 판정 시 이 레벨의 스탯으로 계산한다. */
    // 기존 행이 있는 테이블에 ddl-auto가 NOT NULL 컬럼을 추가할 수 있도록 DB 기본값 명시.
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer level = 1;

    @Builder
    public SiegeForce(SiegeEvent siege, UnitType unitType, Integer quantity, Integer level) {
        this.siege = siege;
        this.unitType = unitType;
        this.quantity = quantity;
        this.level = level != null ? level : 1;
    }

    // 전투 손실 반영 — 생존 수량으로 줄인다.
    public void subtractQuantity(int amount) {
        this.quantity -= amount;
    }
}
