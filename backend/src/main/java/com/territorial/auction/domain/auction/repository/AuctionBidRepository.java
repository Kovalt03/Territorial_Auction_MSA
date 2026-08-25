package com.territorial.auction.domain.auction.repository;

import com.territorial.auction.domain.auction.entity.AuctionBid;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    List<AuctionBid> findTop5ByAuctionIdOrderByBidAtDesc(Long auctionId);

    List<AuctionBid> findAllByAuctionIdOrderByBidAtAsc(Long auctionId);

    Optional<AuctionBid> findTopByAuctionIdAndBidderIdOrderByPriceDesc(
            Long auctionId, Long bidderId);

    @Query(
            value =
                    "SELECT ab FROM AuctionBid ab"
                            + " JOIN FETCH ab.auction a"
                            + " JOIN FETCH a.territory t"
                            + " JOIN FETCH t.continent"
                            + " JOIN FETCH t.grade"
                            + " WHERE ab.bidder.id = :userId",
            countQuery = "SELECT COUNT(ab) FROM AuctionBid ab WHERE ab.bidder.id = :userId")
    Page<AuctionBid> findAllByBidderIdWithAuction(@Param("userId") Long userId, Pageable pageable);

    /** 사용자가 입찰한 영토별 가장 최근 입찰 1건씩 반환 (동일 영토 재경매 포함 중복 제거) */
    @Query(
            "SELECT ab FROM AuctionBid ab"
                    + " JOIN FETCH ab.auction a"
                    + " JOIN FETCH a.territory t"
                    + " JOIN FETCH t.continent"
                    + " JOIN FETCH t.grade"
                    + " WHERE ab.bidder.id = :userId"
                    + " AND ab.id IN ("
                    + "   SELECT MAX(ab2.id) FROM AuctionBid ab2"
                    + "   WHERE ab2.bidder.id = :userId"
                    + "   GROUP BY ab2.auction.territory.id"
                    + " )"
                    + " ORDER BY ab.id DESC")
    List<AuctionBid> findLatestBidPerAuctionByBidder(@Param("userId") Long userId);

    @Query(
            "SELECT DISTINCT ab.bidder.id FROM AuctionBid ab"
                    + " WHERE ab.auction.id = :auctionId"
                    + " AND ab.bidder IS NOT NULL"
                    + " AND ab.bidder.id != :winnerId")
    List<Long> findDistinctBidderIdsExcluding(
            @Param("auctionId") Long auctionId, @Param("winnerId") Long winnerId);
}
