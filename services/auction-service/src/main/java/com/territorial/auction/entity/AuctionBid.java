package com.territorial.auction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "auction_bids",
        indexes = {
            @Index(name = "idx_auction_bids_auction_bid_at", columnList = "auction_id, bid_at ASC")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AuctionBid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    //    @ManyToOne(fetch = FetchType.LAZY)
    //    @JoinColumn(name = "bidder_id")
    //    private User bidder; // NULL = 시스템(시작가)
    @Column(name = "bidder_id")
    private Long bidderId;

    private String bidderNickname;

    @Column(nullable = false)
    private Integer price;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime bidAt;

    @Builder
    public AuctionBid(Auction auction, Long bidderId, String bidderNickname, Integer price) {
        this.auction = auction;
        this.bidderId = bidderId;
        this.bidderNickname = bidderNickname;
        this.price = price;
    }
}
