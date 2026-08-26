package com.territorial.auction.entity;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.user.entity.User;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id", nullable = false)
    private Territory territory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id", nullable = false)
    private User winner;

    @Column(nullable = false)
    private Integer finalPrice;

    @Column(nullable = false)
    private LocalDateTime wonAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = true)
    private Season season;

    @Builder
    public AuctionHistory(
            Auction auction,
            Territory territory,
            User winner,
            Integer finalPrice,
            LocalDateTime wonAt,
            Season season) {
        this.auction = auction;
        this.territory = territory;
        this.winner = winner;
        this.finalPrice = finalPrice;
        this.wonAt = wonAt;
        this.season = season;
    }
}
