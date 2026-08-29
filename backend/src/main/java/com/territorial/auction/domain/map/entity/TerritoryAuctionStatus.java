package com.territorial.auction.domain.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * map 읽기 프로젝션 — 영토별 '경매중' 상태. auction-service의 auction.opened/bid/closed 이벤트로만 갱신되는 read-model이다.
 * 영토당 활성 경매는 하나이므로 territoryId를 PK로 둔다. 행 존재 + endAt이 미래 ⟺ 경매 진행 중.
 */
@Entity
@Table(name = "territory_auction_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryAuctionStatus {

    @Id
    @Column(name = "territory_id")
    private Long territoryId;

    @Column(nullable = false)
    private Long auctionId;

    @Column(nullable = false)
    private int currentPrice;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Builder
    public TerritoryAuctionStatus(
            Long territoryId, Long auctionId, int currentPrice, LocalDateTime endAt) {
        this.territoryId = territoryId;
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.endAt = endAt;
    }

    public void updateBid(int currentPrice, LocalDateTime endAt) {
        this.currentPrice = currentPrice;
        this.endAt = endAt;
    }
}
