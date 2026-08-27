package com.territorial.auction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "territory_id", nullable = false)
    private Long territoryId;

    @Column(nullable = false)
    private Integer coordX;

    @Column(nullable = false)
    private Integer coordY;

    @Column(nullable = false)
    private String continentName;

    // 대륙 필터(getAuctions)용 스냅샷. 경매 생성(#3) 시 territory의 continentId를 복사.
    private Long continentId;

    @Column(nullable = false)
    private String grade;

    @Column(name = "current_bidder_id")
    private Long currentBidderId;

    private String currentBidderNickname;

    @Column(nullable = false)
    private Integer currentPrice;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDateTime maxExtendUntil;

    @Column(nullable = false)
    private boolean settled = false;

    @Builder
    public Auction(
            Long territoryId,
            Integer coordX,
            Integer coordY,
            String continentName,
            Long continentId,
            String grade,
            Integer currentPrice,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime maxExtendUntil) {
        this.territoryId = territoryId;
        this.coordX = coordX;
        this.coordY = coordY;
        this.continentName = continentName;
        this.continentId = continentId;
        this.grade = grade;
        this.currentPrice = currentPrice;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxExtendUntil = maxExtendUntil;
    }

    public boolean isEnded() {
        return LocalDateTime.now().isAfter(this.endAt);
    }

    public void updateBid(Long bidderId, String bidderNickname, int newPrice) {
        this.currentBidderId = bidderId;
        this.currentBidderNickname = bidderNickname;
        this.currentPrice = newPrice;
    }

    public void extendEndAt(LocalDateTime newEndAt) {
        this.endAt = newEndAt.isAfter(this.maxExtendUntil) ? this.maxExtendUntil : newEndAt;
    }

    public void settle() {
        this.settled = true;
    }
}
