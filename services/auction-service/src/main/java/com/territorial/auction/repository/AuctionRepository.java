package com.territorial.auction.repository;

import com.territorial.auction.entity.Auction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Boolean existsByTerritoryId(Long territoryId);

    Optional<Auction> findFirstByTerritoryIdAndSettledFalseOrderByEndAtDesc(Long territoryId);

    // 관계(territory·continent·grade·currentBidder) 제거됨 — 표시 데이터는 Auction 스냅샷 필드.
    // 대륙 필터는 continentId 스냅샷으로.
    @Query(
            value =
                    "SELECT a FROM Auction a"
                            + " WHERE (:continentId IS NULL OR a.continentId = :continentId)"
                            + " AND (:status IS NULL"
                            + "   OR (:status = 'BIDDING' AND a.endAt > :now)"
                            + "   OR (:status = 'IDLE'    AND a.endAt <= :now))",
            countQuery =
                    "SELECT COUNT(a) FROM Auction a"
                            + " WHERE (:continentId IS NULL OR a.continentId = :continentId)"
                            + " AND (:status IS NULL"
                            + "   OR (:status = 'BIDDING' AND a.endAt > :now)"
                            + "   OR (:status = 'IDLE'    AND a.endAt <= :now))")
    Page<Auction> findAllWithFilter(
            @Param("continentId") Long continentId,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.endAt <= :now AND a.settled = false")
    List<Auction> findAllExpiredUnsettled(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(a) FROM Auction a WHERE a.settled = false AND a.endAt > :now")
    long countActiveAuctions(@Param("now") LocalDateTime now);
}
