package com.territorial.auction.domain.military.repository;

import com.territorial.auction.domain.military.entity.UnitInstance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitInstanceRepository extends JpaRepository<UnitInstance, Long> {

    List<UnitInstance> findByUserId(Long userId);

    List<UnitInstance> findByUserIdAndDeployedTerritoryId(Long userId, Long territoryId);

    // 영토 상실 정산용 — 그 영토에 귀속됐거나 배치된 소유자 유닛 전부(대기+배치+이동중).
    // 공격자 유닛도 deployedTerritory 로 잡히므로 반드시 소유자로 필터한다.
    @Query(
            "SELECT u FROM UnitInstance u WHERE u.user.id = :userId"
                    + " AND (u.homeTerritory.id = :territoryId OR u.deployedTerritory.id = :territoryId)")
    List<UnitInstance> findByOwnerAndTerritoryAssociation(
            @Param("userId") Long userId, @Param("territoryId") Long territoryId);

    // ─── 대기(ready idle) 스택 — 배치 안 됨 + 이동 중 아님. (귀속지·레벨)별로 유일하게 유지·병합 ──
    // 레벨이 다르면 스탯이 다르므로 별도 스택으로 관리한다.

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeTerritory.id = :homeTerritoryId"
                    + " AND u.deployedTerritory IS NULL AND u.moveCompleteAt IS NULL")
    Optional<UnitInstance> findReadyIdleAtTerritory(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeTerritoryId") Long homeTerritoryId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeIsland.id = :homeIslandId"
                    + " AND u.deployedTerritory IS NULL AND u.moveCompleteAt IS NULL")
    Optional<UnitInstance> findReadyIdleAtIsland(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeIslandId") Long homeIslandId);

    // ─── 배치(deployed) 스택 — (유저·타입·레벨·귀속지·배치건물) 조합으로 병합 ────────────────

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeTerritory.id = :homeTerritoryId"
                    + " AND u.deployedBuilding.id = :buildingId")
    Optional<UnitInstance> findDeployedFromTerritory(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeTerritoryId") Long homeTerritoryId,
            @Param("buildingId") Long buildingId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeIsland.id = :homeIslandId"
                    + " AND u.deployedBuilding.id = :buildingId")
    Optional<UnitInstance> findDeployedFromIsland(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeIslandId") Long homeIslandId,
            @Param("buildingId") Long buildingId);

    // 특정 건물에 주둔한 총 수량 — 건물별 주둔 수용량 검증용
    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u"
                    + " WHERE u.deployedBuilding.id = :buildingId")
    Integer sumQuantityByDeployedBuildingId(@Param("buildingId") Long buildingId);

    // 특정 건물에 주둔한 스택 전부 — 건물 파괴 시 퇴각용
    List<UnitInstance> findByDeployedBuildingId(Long deployedBuildingId);

    // 특정 영토의 공격받는 Zone에 주둔한 방어 병력 — 공성 판정용
    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.deployedTerritory.id = :territoryId"
                    + " AND u.deployedBuilding.zone = :zone")
    List<UnitInstance> findDefendersInZone(
            @Param("userId") Long userId,
            @Param("territoryId") Long territoryId,
            @Param("zone") Integer zone);

    // 특정 영토에 배치된 (유저·타입·레벨) 스택 전부 — 귀속지가 달라 여럿일 수 있다(회수용)
    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.deployedTerritory.id = :territoryId"
                    + " ORDER BY u.id ASC")
    List<UnitInstance> findDeployedAtTerritory(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("territoryId") Long territoryId);

    // ─── 위치별 수량 합산 — 귀속지 기준 수용량 산정 (대기+이동중+배치 전부 포함) ───────────────

    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u"
                    + " WHERE u.homeTerritory.id = :territoryId")
    Integer sumQuantityByHomeTerritoryId(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u"
                    + " WHERE u.homeIsland.id = :islandId")
    Integer sumQuantityByHomeIslandId(@Param("islandId") Long islandId);

    // 대기(ready idle) 유닛 총합 — 공성 선언 가용량 검증용 (귀속지 무관, 레벨별)
    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level"
                    + " AND u.deployedTerritory IS NULL AND u.moveCompleteAt IS NULL")
    Integer sumReadyIdleQuantity(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level);

    // 대기(ready idle) 스택 전부 — 공성 병력 커밋 시 차감용 (귀속지 무관, 레벨별)
    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.user.id = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level"
                    + " AND u.deployedTerritory IS NULL AND u.moveCompleteAt IS NULL")
    List<UnitInstance> findReadyIdleByUserIdAndUnitTypeIdAndLevel(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level);

    // 이동 완료 시각이 도래한 이동중 스택 — 스케줄러 정산용
    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.moveCompleteAt IS NOT NULL AND u.moveCompleteAt <= :now")
    List<UnitInstance> findArrivedInTransit(@Param("now") LocalDateTime now);

    @Query(
            "SELECT u.deployedTerritory.id, SUM(u.quantity) FROM UnitInstance u"
                    + " WHERE u.deployedTerritory.id IN :territoryIds"
                    + " GROUP BY u.deployedTerritory.id")
    List<Object[]> sumQuantityGroupByTerritoryIds(@Param("territoryIds") List<Long> territoryIds);
}
