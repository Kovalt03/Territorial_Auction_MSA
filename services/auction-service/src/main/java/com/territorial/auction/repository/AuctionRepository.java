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

    // 재시도 대상: 종료됐고 미정산이며 재시도 상한에 아직 도달하지 않은 경매.
    // settle_attempts >= 상한인 건은 "정산 실패"로 루프에서 제외된다.
    @Query(
            "SELECT a FROM Auction a"
                    + " WHERE a.endAt <= :now AND a.settled = false"
                    + " AND a.settleAttempts < :maxAttempts")
    List<Auction> findRetriableExpired(
            @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    // 보상 대상: 재시도 상한 도달했으나 미정산인 경매(전진복구 실패 → 되돌림 필요).
    // 낙찰자 잠금 AP 환불 + 영토 해제 + 정산 종료로 dangling 상태를 제거한다.
    @Query(
            "SELECT a FROM Auction a"
                    + " WHERE a.endAt <= :now AND a.settled = false"
                    + " AND a.settleAttempts >= :maxAttempts")
    List<Auction> findAbandonedSettlements(
            @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Query("SELECT COUNT(a) FROM Auction a WHERE a.settled = false AND a.endAt > :now")
    long countActiveAuctions(@Param("now") LocalDateTime now);

    // 관리자 목록 — 진행 중(미정산·미종료) 경매. 표시 데이터는 스냅샷 필드라 조인 불필요.
    @Query(
            value = "SELECT a FROM Auction a WHERE a.settled = false AND a.endAt > :now",
            countQuery =
                    "SELECT COUNT(a) FROM Auction a WHERE a.settled = false AND a.endAt > :now")
    Page<Auction> findActiveForAdmin(@Param("now") LocalDateTime now, Pageable pageable);
}
