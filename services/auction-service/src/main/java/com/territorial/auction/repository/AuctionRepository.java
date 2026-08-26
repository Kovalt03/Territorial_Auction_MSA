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

    @Query(
            value =
                    "SELECT a FROM Auction a"
                            + " JOIN FETCH a.territory t"
                            + " JOIN FETCH t.continent"
                            + " JOIN FETCH t.grade"
                            + " LEFT JOIN FETCH a.currentBidder"
                            + " WHERE (:continentId IS NULL OR t.continent.id = :continentId)"
                            + " AND (:status IS NULL"
                            + "   OR (:status = 'BIDDING' AND a.endAt > :now)"
                            + "   OR (:status = 'IDLE'    AND a.endAt <= :now))",
            countQuery =
                    "SELECT COUNT(a) FROM Auction a"
                            + " JOIN a.territory t"
                            + " WHERE (:continentId IS NULL OR t.continent.id = :continentId)"
                            + " AND (:status IS NULL"
                            + "   OR (:status = 'BIDDING' AND a.endAt > :now)"
                            + "   OR (:status = 'IDLE'    AND a.endAt <= :now))")
    Page<Auction> findAllWithFilter(
            @Param("continentId") Long continentId,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(
            "SELECT a FROM Auction a"
                    + " JOIN FETCH a.territory t"
                    + " JOIN FETCH t.grade"
                    + " LEFT JOIN FETCH a.currentBidder"
                    + " WHERE a.endAt <= :now AND a.settled = false")
    List<Auction> findAllExpiredUnsettled(@Param("now") LocalDateTime now);

    @Query(
            "SELECT a.territory.id FROM Auction a"
                    + " WHERE a.territory.id IN :territoryIds"
                    + " AND a.settled = false"
                    + " AND a.endAt > :now")
    List<Long> findActiveAuctionTerritoryIds(
            @Param("territoryIds") List<Long> territoryIds, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(a) FROM Auction a WHERE a.settled = false AND a.endAt > :now")
    long countActiveAuctions(@Param("now") LocalDateTime now);

    @Query(
            "SELECT a FROM Auction a"
                    + " JOIN FETCH a.territory t"
                    + " JOIN FETCH t.continent"
                    + " JOIN FETCH t.grade"
                    + " LEFT JOIN FETCH a.currentBidder"
                    + " WHERE a.id = :id")
    Optional<Auction> findByIdWithDetails(@Param("id") Long id);

    @Query(
            value =
                    "SELECT a FROM Auction a"
                            + " JOIN FETCH a.territory t"
                            + " JOIN FETCH t.continent"
                            + " JOIN FETCH t.grade"
                            + " LEFT JOIN FETCH a.currentBidder"
                            + " WHERE a.settled = false AND a.endAt > :now",
            countQuery =
                    "SELECT COUNT(a) FROM Auction a WHERE a.settled = false AND a.endAt > :now")
    Page<Auction> findActiveForAdmin(@Param("now") LocalDateTime now, Pageable pageable);
}
