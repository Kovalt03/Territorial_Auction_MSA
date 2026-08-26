package com.territorial.auction.entity;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.user.entity.User;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id", nullable = false)
    private Territory territory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_bidder_id")
    private User currentBidder;

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
            Territory territory,
            Integer currentPrice,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime maxExtendUntil) {
        this.territory = territory;
        this.currentPrice = currentPrice;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxExtendUntil = maxExtendUntil;
    }

    public boolean isEnded() {
        return LocalDateTime.now().isAfter(this.endAt);
    }

    public void updateBid(User bidder, int newPrice) {
        this.currentBidder = bidder;
        this.currentPrice = newPrice;
    }

    public void extendEndAt(LocalDateTime newEndAt) {
        this.endAt = newEndAt.isAfter(this.maxExtendUntil) ? this.maxExtendUntil : newEndAt;
    }

    public void settle() {
        this.settled = true;
    }
}
