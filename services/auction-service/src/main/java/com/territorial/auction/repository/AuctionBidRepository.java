package com.territorial.auction.repository;

import com.territorial.auction.entity.AuctionBid;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    List<AuctionBid> findTop5ByAuctionIdOrderByBidAtDesc(Long auctionId);

    List<AuctionBid> findAllByAuctionIdOrderByBidAtAsc(Long auctionId);

    /** 사용자가 입찰한 영토별 가장 최근 입찰 1건씩 (auction은 자기 도메인 관계라 FETCH 유지, bidder는 ID 스냅샷) */
    @Query(
            "SELECT ab FROM AuctionBid ab"
                    + " JOIN FETCH ab.auction a"
                    + " WHERE ab.bidderId = :userId"
                    + " AND ab.id IN ("
                    + "   SELECT MAX(ab2.id) FROM AuctionBid ab2"
                    + "   WHERE ab2.bidderId = :userId"
                    + "   GROUP BY ab2.auction.territoryId"
                    + " )"
                    + " ORDER BY ab.id DESC")
    List<AuctionBid> findLatestBidPerAuctionByBidder(@Param("userId") Long userId);

    @Query(
            "SELECT DISTINCT ab.bidderId FROM AuctionBid ab"
                    + " WHERE ab.auction.id = :auctionId"
                    + " AND ab.bidderId IS NOT NULL"
                    + " AND ab.bidderId != :winnerId")
    List<Long> findDistinctBidderIdsExcluding(
            @Param("auctionId") Long auctionId, @Param("winnerId") Long winnerId);
}
