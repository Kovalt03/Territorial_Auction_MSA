package com.territorial.auction.domain.military.entity;

import jakarta.persistence.*;
import lombok.*;

/** 한 공성전에 공격자가 세운 공성 건물. 대상 영토 인접 타일 좌표에 배치되며 해당 공성에 종속된다. 판정 후 전부 삭제된다. */
@Entity
@Table(name = "siege_structures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiegeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siege_id", nullable = false)
    private SiegeEvent siege;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SiegeStructureType type;

    @Column(name = "coord_x", nullable = false)
    private Integer coordX;

    @Column(name = "coord_y", nullable = false)
    private Integer coordY;

    @Builder
    public SiegeStructure(
            SiegeEvent siege, SiegeStructureType type, Integer coordX, Integer coordY) {
        this.siege = siege;
        this.type = type;
        this.coordX = coordX;
        this.coordY = coordY;
    }
}
