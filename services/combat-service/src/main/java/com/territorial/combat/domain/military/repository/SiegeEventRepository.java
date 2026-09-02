package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.SiegeEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiegeEventRepository extends JpaRepository<SiegeEvent, Long> {

    Page<SiegeEvent> findByStatus(SiegeEvent.SiegeStatus status, Pageable pageable);

    @Query(
            "SELECT s FROM SiegeEvent s"
                    + " WHERE (s.attackerId = :userId OR s.defenderId = :userId)"
                    + " AND s.status = :status")
    Page<SiegeEvent> findMyHistory(
            @Param("userId") Long userId,
            @Param("status") SiegeEvent.SiegeStatus status,
            Pageable pageable);

    @Query("SELECT s FROM SiegeEvent s WHERE s.status = :status AND s.resolveAt <= :now")
    List<SiegeEvent> findPendingToResolve(
            @Param("status") SiegeEvent.SiegeStatus status, @Param("now") LocalDateTime now);

    @Query(
            "SELECT s FROM SiegeEvent s"
                    + " WHERE s.targetTerritoryId = :territoryId"
                    + " AND s.attackerId = :attackerId"
                    + " AND s.status = :status"
                    + " ORDER BY s.resolveAt DESC")
    List<SiegeEvent> findRecentByTerritoryAndAttacker(
            @Param("territoryId") Long territoryId,
            @Param("attackerId") Long attackerId,
            @Param("status") SiegeEvent.SiegeStatus status);
}
