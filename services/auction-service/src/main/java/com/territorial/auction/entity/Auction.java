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

    // 정산 실패 누적 횟수. 상한 도달 시 재시도 루프에서 제외(정산 실패로 종료).
    @Column(nullable = false)
    private Integer settleAttempts = 0;

    // 낙관적 락. 분산락이 lease 만료로 뚫려 동시 입찰이 들어와도 커밋은 하나만 성공하게 한다(정확성 안전벨트).
    @Version
    @Column(nullable = false)
    private Long version;

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

    /** 정산 실패 1회 누적. 반환값이 상한 이상이면 재시도를 멈추고 정산 실패로 종료할 시점이다. */
    public int recordFailedSettleAttempt() {
        this.settleAttempts += 1;
        return this.settleAttempts;
    }
}
