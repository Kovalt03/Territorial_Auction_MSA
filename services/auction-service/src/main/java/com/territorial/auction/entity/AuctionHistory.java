package com.territorial.auction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "auction_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(name = "territory_id", nullable = false)
    private Long territoryId;

    @Column(name = "winner_id", nullable = false)
    private Long winnerId;

    private String winnerName;

    @Column(nullable = false)
    private Integer finalPrice;

    @Column(nullable = false)
    private LocalDateTime wonAt;

    @Column(name = "season_id", nullable = true)
    private Long seasonId;

    @Builder
    public AuctionHistory(
            Auction auction,
            Long territoryId,
            Long winnerId,
            String winnerName,
            Integer finalPrice,
            LocalDateTime wonAt,
            Long seasonId) {
        this.auction = auction;
        this.territoryId = territoryId;
        this.winnerId = winnerId;
        this.winnerName = winnerName;
        this.finalPrice = finalPrice;
        this.wonAt = wonAt;
        this.seasonId = seasonId;
    }
}
