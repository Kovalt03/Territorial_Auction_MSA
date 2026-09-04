package com.territorial.map.domain.map.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "territories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Territory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coord_x", nullable = false)
    private Integer coordX;

    @Column(name = "coord_y", nullable = false)
    private Integer coordY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "continent_id")
    private Continent continent;

    // owner는 user-service 소유라 FK 없이 식별자만 보관한다.
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(length = 7)
    private String currentColor;

    private LocalDateTime occupiedUntil;

    // 공성 보호 만료 시각. 이 시각 전까지는 공성전 불가. 점유(occupiedUntil)와 별개(보호 < 점유).
    private LocalDateTime protectedUntil;

    private LocalDateTime nextAuctionAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TerritoryStatus status = TerritoryStatus.IDLE;

    @Column(nullable = false)
    private Integer baseProductionRate = 10;

    // 관리자가 경매 대상에서 제외한 영토는 IDLE이어도 신규 경매가 생성되지 않는다.
    @Column(nullable = false)
    private Boolean auctionEnabled = true;

    private LocalDateTime lastProducedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private TerritoryGrade grade;

    @Builder
    public Territory(Integer coordX, Integer coordY, Continent continent, TerritoryGrade grade) {
        this.coordX = coordX;
        this.coordY = coordY;
        this.continent = continent;
        this.grade = grade;
    }

    public void updateColor(String colorCode) {
        this.currentColor = colorCode;
    }

    // 관리자 등급 변경. 다음 경매 시작가·생산량에 반영된다.
    public void changeGrade(TerritoryGrade grade) {
        this.grade = grade;
    }

    public void changeAuctionEnabled(boolean enabled) {
        this.auctionEnabled = enabled;
    }

    // IDLE 영토를 경매 순환에 편입: 다음 경매 예약 시각만 설정(상태는 유지).
    public void scheduleNextAuction(LocalDateTime nextAuctionAt) {
        this.nextAuctionAt = nextAuctionAt;
    }

    public void startBidding() {
        this.status = TerritoryStatus.BIDDING;
        this.nextAuctionAt = null;
    }

    public void occupy(Long ownerId, LocalDateTime occupiedUntil, LocalDateTime protectedUntil) {
        this.ownerId = ownerId;
        this.status = TerritoryStatus.OCCUPIED;
        this.occupiedUntil = occupiedUntil;
        this.protectedUntil = protectedUntil;
        this.nextAuctionAt = null;
        this.lastProducedAt = LocalDateTime.now();
    }

    public void release(LocalDateTime nextAuctionAt) {
        this.status = TerritoryStatus.IDLE;
        this.ownerId = null;
        this.occupiedUntil = null;
        this.protectedUntil = null;
        this.nextAuctionAt = nextAuctionAt;
        this.lastProducedAt = null;
    }

    public void updateLastProducedAt(LocalDateTime now) {
        this.lastProducedAt = now;
    }

    public enum TerritoryStatus {
        BIDDING,
        OCCUPIED,
        IDLE
    }
}
