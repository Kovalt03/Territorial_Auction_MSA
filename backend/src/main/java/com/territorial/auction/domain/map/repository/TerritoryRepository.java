package com.territorial.auction.domain.map.repository;

import com.territorial.auction.domain.map.entity.Territory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TerritoryRepository extends JpaRepository<Territory, Long> {

    long countByOwnerId(Long ownerId);

    long countByStatus(Territory.TerritoryStatus status);

    List<Territory> findAllByStatusAndNextAuctionAtIsNull(Territory.TerritoryStatus status);

    // 대륙 전체 경매 활성/비활성 일괄 변경
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Territory t SET t.auctionEnabled = :enabled WHERE t.continent.id = :continentId")
    int updateAuctionEnabledByContinentId(
            @Param("continentId") Long continentId, @Param("enabled") boolean enabled);

    long countByOwner_IdIn(List<Long> ownerIds);

    @Query("SELECT t FROM Territory t JOIN FETCH t.continent JOIN FETCH t.grade")
    List<Territory> findAllWithContinentAndGrade();

    List<Territory> findAllByContinentId(Long continentId);

    @Query(
            "SELECT t FROM Territory t JOIN FETCH t.continent JOIN FETCH t.grade LEFT JOIN FETCH t.owner WHERE t.id = :id")
    Optional<Territory> findByIdWithDetails(@Param("id") Long id);

    long countByContinentId(Long continentId);

    long countByContinentIdAndStatus(Long continentId, Territory.TerritoryStatus status);

    @Query("SELECT t.continent.id, COUNT(t) FROM Territory t GROUP BY t.continent.id")
    List<Object[]> countGroupByContinent();

    // 관리자 대륙 구성 현황: 대륙 × 등급 × 상태 집계 → [continentId, grade, status, count]
    @Query(
            "SELECT t.continent.id, g.grade, t.status, COUNT(t) "
                    + "FROM Territory t JOIN t.grade g "
                    + "GROUP BY t.continent.id, g.grade, t.status")
    List<Object[]> aggregateCompositionGroupByContinent();

    // 관리자 영토 목록(그리드용): 등급·소유자 fetch, 좌표순
    @Query(
            "SELECT t FROM Territory t JOIN FETCH t.grade LEFT JOIN FETCH t.owner "
                    + "WHERE t.continent.id = :continentId ORDER BY t.coordY, t.coordX")
    List<Territory> findAllByContinentIdWithDetails(@Param("continentId") Long continentId);

    @Query(
            "SELECT t.continent.id, COUNT(t) FROM Territory t WHERE t.status = :status GROUP BY t.continent.id")
    List<Object[]> countByStatusGroupByContinent(@Param("status") Territory.TerritoryStatus status);

    @Query(
            "SELECT t.owner.id, COUNT(t) FROM Territory t WHERE t.owner.id IN :ownerIds AND t.status = :status GROUP BY t.owner.id")
    List<Object[]> countGroupByOwnerIds(
            @Param("ownerIds") List<Long> ownerIds,
            @Param("status") Territory.TerritoryStatus status);

    @Query(
            value =
                    "SELECT t FROM Territory t JOIN FETCH t.continent JOIN FETCH t.grade where t.owner.id = :userId",
            countQuery = "SELECT COUNT(t) FROM Territory t WHERE t.owner.id = :userId")
    Page<Territory> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(
            "SELECT t FROM Territory t"
                    + " JOIN FETCH t.grade"
                    + " JOIN FETCH t.continent"
                    + " WHERE t.status = :status AND t.occupiedUntil <= :now")
    List<Territory> findAllExpiredOccupied(
            @Param("status") Territory.TerritoryStatus status, @Param("now") LocalDateTime now);

    @Query(
            "SELECT t FROM Territory t"
                    + " JOIN FETCH t.grade"
                    + " JOIN FETCH t.continent"
                    + " WHERE t.status = :status"
                    + " AND t.auctionEnabled = true"
                    + " AND t.nextAuctionAt IS NOT NULL"
                    + " AND t.nextAuctionAt <= :now")
    List<Territory> findAllReadyForAuction(
            @Param("status") Territory.TerritoryStatus status, @Param("now") LocalDateTime now);

    @Query(
            "SELECT DISTINCT t.owner.id FROM Territory t WHERE t.owner IS NOT NULL AND t.status = :status")
    List<Long> findAllDistinctOwnerIds(@Param("status") Territory.TerritoryStatus status);

    List<Territory> findByOwnerId(Long ownerId);

    @Query("SELECT t FROM Territory t WHERE t.owner.id = :userId AND t.status = :status")
    List<Territory> findAllOccupiedByOwnerId(
            @Param("userId") Long userId, @Param("status") Territory.TerritoryStatus status);

    @Query(
            "SELECT COUNT(t) FROM Territory t"
                    + " WHERE t.status = :status"
                    + " AND t.owner.id = :ownerId"
                    + " AND t.id <> :excludeId"
                    + " AND ((t.coordX = :x AND (t.coordY = :y - 1 OR t.coordY = :y + 1))"
                    + "   OR (t.coordY = :y AND (t.coordX = :x - 1 OR t.coordX = :x + 1)))")
    int countAdjacentOccupiedByOwner(
            @Param("x") int x,
            @Param("y") int y,
            @Param("ownerId") Long ownerId,
            @Param("excludeId") Long excludeId,
            @Param("status") Territory.TerritoryStatus status);
}
