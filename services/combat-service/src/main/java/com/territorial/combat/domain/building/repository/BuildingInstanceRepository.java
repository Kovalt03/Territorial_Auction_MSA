package com.territorial.combat.domain.building.repository;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingInstanceRepository extends JpaRepository<BuildingInstance, Long> {

    interface MilitaryLocationSummary {
        Integer getMaxBarracksLevel();

        Integer getCastleLevel();

        Integer getResidenceCapacity();
    }

    long countByBuildingType_Id(Long buildingTypeId);

    /**
     * 유저가 지금 짓거나 업그레이드하고 있는 건물 수 — 건축 장인 슬롯 점유량.
     *
     * <p>건물은 섬 또는 영토 중 한쪽에만 속하므로 암시적 조인(INNER)을 쓰면 두 경로 모두 걸러진다. LEFT JOIN 으로 명시한다.
     */
    @Query(
            "SELECT COUNT(b) FROM BuildingInstance b"
                    + " LEFT JOIN b.island i"
                    + " WHERE b.buildCompleteAt > :now"
                    + " AND (i.userId = :userId OR b.territoryId IN :territoryIds)")
    long countUnderConstructionByOwnerId(
            @Param("userId") Long userId,
            @Param("territoryIds") List<Long> territoryIds,
            @Param("now") LocalDateTime now);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType WHERE b.territoryId = :territoryId AND b.posX >= 0")
    List<BuildingInstance> findByTerritoryId(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType WHERE b.island.id = :islandId AND b.posX >= 0")
    List<BuildingInstance> findByIslandId(@Param("islandId") Long islandId);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType WHERE b.ownerId = :userId AND b.territoryId IS NULL AND b.island IS NULL")
    List<BuildingInstance> findStoredByOwnerId(@Param("userId") Long userId);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.territoryId = :territoryId AND b.zone = :zone AND b.isDestroyed = false")
    List<BuildingInstance> findActiveByTerritoryIdAndZone(
            @Param("territoryId") Long territoryId, @Param("zone") Integer zone);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.territoryId = :territoryId"
                    + " AND b.buildingType.name = 'STORAGE'"
                    + " AND b.posX >= 0"
                    + " AND b.isDestroyed = false")
    Optional<BuildingInstance> findActiveStorageByTerritoryId(
            @Param("territoryId") Long territoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType WHERE b.id = :id")
    Optional<BuildingInstance> findByIdWithLock(@Param("id") Long id);

    /**
     * 위치(영토)의 GP·식량 저장 건물을 락과 함께 조회한다 — 성·저장소 모두. 정렬은 호출측(GlobalVaultService)에서 명시적으로 한다 — JOIN
     * FETCH 는 ORDER BY 를 무시할 수 있다. 파괴 여부 무관 — 정산·이전은 파괴된 저장소도 대상이 될 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.territoryId = :territoryId"
                    + " AND b.buildingType.name IN ('STORAGE', 'CASTLE')"
                    + " AND b.posX >= 0")
    List<BuildingInstance> findStorageBuildingsByTerritoryIdWithLock(
            @Param("territoryId") Long territoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.island.id = :islandId"
                    + " AND b.buildingType.name IN ('STORAGE', 'CASTLE')"
                    + " AND b.posX >= 0")
    List<BuildingInstance> findStorageBuildingsByIslandIdWithLock(@Param("islandId") Long islandId);

    // 락 없는 조회 — 표시용 (읽기 트랜잭션)
    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.territoryId = :territoryId"
                    + " AND b.buildingType.name IN ('STORAGE', 'CASTLE')"
                    + " AND b.posX >= 0")
    List<BuildingInstance> findStorageBuildingsByTerritoryId(
            @Param("territoryId") Long territoryId);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.island.id = :islandId"
                    + " AND b.buildingType.name IN ('STORAGE', 'CASTLE')"
                    + " AND b.posX >= 0")
    List<BuildingInstance> findStorageBuildingsByIslandId(@Param("islandId") Long islandId);

    // ─── 위치(영토/섬)별 병영·성·주거지 조회 — 유닛 생산 위치 스코핑용 ───────────────

    @Query(
            "SELECT COUNT(b) > 0 FROM BuildingInstance b"
                    + " WHERE b.territoryId = :territoryId AND b.buildingType.name = 'BARRACKS'"
                    + " AND b.isDestroyed = false")
    boolean existsActiveBarracksByTerritoryId(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT COUNT(b) > 0 FROM BuildingInstance b"
                    + " WHERE b.island.id = :islandId AND b.buildingType.name = 'BARRACKS'"
                    + " AND b.isDestroyed = false")
    boolean existsActiveBarracksByIslandId(@Param("islandId") Long islandId);

    @Query(
            "SELECT MAX(b.level) FROM BuildingInstance b"
                    + " WHERE b.territoryId = :territoryId AND b.buildingType.name = 'BARRACKS'"
                    + " AND b.isDestroyed = false")
    Optional<Integer> findMaxBarracksLevelByTerritoryId(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT MAX(b.level) FROM BuildingInstance b"
                    + " WHERE b.island.id = :islandId AND b.buildingType.name = 'BARRACKS'"
                    + " AND b.isDestroyed = false")
    Optional<Integer> findMaxBarracksLevelByIslandId(@Param("islandId") Long islandId);

    // 연구는 계정 단위 — 유저 소유 모든 위치(영토·섬)의 RESEARCH_LAB 중 최고 레벨. 이것이 연구 가능 상한.
    // 섬·영토를 OR로 섞을 땐 반드시 LEFT JOIN — 암묵적 조인이면 둘 다 INNER JOIN이 걸려
    // 한쪽이 null인(섬 건물은 territory null) 행이 전부 제외돼 항상 0이 된다.
    @Query(
            "SELECT MAX(b.level) FROM BuildingInstance b"
                    + " LEFT JOIN b.island isl"
                    + " WHERE b.buildingType.name = 'RESEARCH_LAB' AND b.isDestroyed = false"
                    + " AND (isl.userId = :userId OR b.territoryId IN :territoryIds)")
    Optional<Integer> findMaxResearchLabLevelByUserId(
            @Param("userId") Long userId, @Param("territoryIds") List<Long> territoryIds);

    @Query(
            "SELECT b.level FROM BuildingInstance b"
                    + " WHERE b.territoryId = :territoryId AND b.buildingType.name = 'CASTLE'"
                    + " AND b.isDestroyed = false")
    Optional<Integer> findCastleLevelByTerritoryId(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT COALESCE(SUM(COALESCE(s.unitCapacityPerLevel, b.level * b.buildingType.unitCapacityPerLevel)), 0)"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.territoryId = :territoryId AND b.isDestroyed = false"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)")
    Integer sumResidenceCapacityByTerritoryId(
            @Param("territoryId") Long territoryId, @Param("now") LocalDateTime now);

    @Query(
            "SELECT COALESCE(SUM(COALESCE(s.unitCapacityPerLevel, b.level * b.buildingType.unitCapacityPerLevel)), 0)"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.island.id = :islandId AND b.isDestroyed = false"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)")
    Integer sumResidenceCapacityByIslandId(
            @Param("islandId") Long islandId, @Param("now") LocalDateTime now);

    @Query(
            "SELECT"
                    + " COALESCE(MAX(CASE WHEN b.buildingType.name = 'BARRACKS' AND b.isDestroyed = false THEN b.level ELSE 0 END), 0) AS maxBarracksLevel,"
                    + " COALESCE(MAX(CASE WHEN b.buildingType.name = 'CASTLE' AND b.isDestroyed = false THEN b.level ELSE 0 END), 0) AS castleLevel,"
                    + " COALESCE(SUM(CASE WHEN b.buildingType.name = 'RESIDENCE' AND b.isDestroyed = false"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)"
                    + " THEN COALESCE(s.unitCapacityPerLevel, b.level * b.buildingType.unitCapacityPerLevel) ELSE 0 END), 0) AS residenceCapacity"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.territoryId = :territoryId")
    MilitaryLocationSummary findMilitaryLocationSummaryByTerritoryId(
            @Param("territoryId") Long territoryId, @Param("now") LocalDateTime now);

    @Query(
            "SELECT"
                    + " COALESCE(MAX(CASE WHEN b.buildingType.name = 'BARRACKS' AND b.isDestroyed = false THEN b.level ELSE 0 END), 0) AS maxBarracksLevel,"
                    + " COALESCE(MAX(CASE WHEN b.buildingType.name = 'CASTLE' AND b.isDestroyed = false THEN b.level ELSE 0 END), 0) AS castleLevel,"
                    + " COALESCE(SUM(CASE WHEN b.buildingType.name = 'RESIDENCE' AND b.isDestroyed = false"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)"
                    + " THEN COALESCE(s.unitCapacityPerLevel, b.level * b.buildingType.unitCapacityPerLevel) ELSE 0 END), 0) AS residenceCapacity"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.island.id = :islandId")
    MilitaryLocationSummary findMilitaryLocationSummaryByIslandId(
            @Param("islandId") Long islandId, @Param("now") LocalDateTime now);

    // ─── 위치별 농지 식량 생산 합산 — FarmlandScheduler 위치 적립용 ─────────────────

    @Query(
            "SELECT b.territoryId, SUM(COALESCE(s.foodProductionRate, b.level * b.buildingType.foodProductionRate))"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.isDestroyed = false AND b.territoryId IS NOT NULL"
                    + " AND (b.buildingType.foodProductionRate IS NOT NULL OR s.foodProductionRate IS NOT NULL)"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)"
                    + " GROUP BY b.territoryId")
    List<Object[]> sumFarmlandFoodGroupedByTerritory(@Param("now") LocalDateTime now);

    @Query(
            "SELECT b.island.id, SUM(COALESCE(s.foodProductionRate, b.level * b.buildingType.foodProductionRate))"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.isDestroyed = false AND b.island IS NOT NULL"
                    + " AND (b.buildingType.foodProductionRate IS NOT NULL OR s.foodProductionRate IS NOT NULL)"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)"
                    + " GROUP BY b.island.id")
    List<Object[]> sumFarmlandFoodGroupedByIsland(@Param("now") LocalDateTime now);

    /** GP 생산량을 영토 위치별로 합산 — GP 값(기본/레벨지정)이 있는 건물이면 어떤 종류든 포함 */
    @Query(
            "SELECT b.territoryId, SUM(COALESCE(s.gpProductionRate, b.level * b.buildingType.gpProductionRate))"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.isDestroyed = false AND b.territoryId IS NOT NULL"
                    + " AND (b.buildingType.gpProductionRate IS NOT NULL OR s.gpProductionRate IS NOT NULL)"
                    + " AND (b.workshopDebuffUntil IS NULL OR b.workshopDebuffUntil < :now)"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)"
                    + " GROUP BY b.territoryId")
    List<Object[]> sumWorkshopGpProductionGroupedByTerritory(@Param("now") LocalDateTime now);

    /** GP 생산량을 섬 위치별로 합산 */
    @Query(
            "SELECT b.island.id, SUM(COALESCE(s.gpProductionRate, b.level * b.buildingType.gpProductionRate))"
                    + " FROM BuildingInstance b"
                    + " LEFT JOIN BuildingLevelSpec s ON s.buildingType = b.buildingType AND s.level = b.level"
                    + " WHERE b.isDestroyed = false AND b.island IS NOT NULL"
                    + " AND (b.buildingType.gpProductionRate IS NOT NULL OR s.gpProductionRate IS NOT NULL)"
                    + " AND (b.workshopDebuffUntil IS NULL OR b.workshopDebuffUntil < :now)"
                    + " AND (b.buildCompleteAt IS NULL OR b.buildCompleteAt <= :now)"
                    + " GROUP BY b.island.id")
    List<Object[]> sumWorkshopGpProductionGroupedByIsland(@Param("now") LocalDateTime now);

    /** 전체 저장 공간(성+저장소)의 GP 총합 — 관리자 경제 지표용 */
    @Query(
            "SELECT COALESCE(SUM(b.storedGp), 0) FROM BuildingInstance b"
                    + " WHERE b.buildingType.name IN ('STORAGE', 'CASTLE') AND b.posX >= 0")
    long sumAllStoredGp();

    /** 특정 소유자의 저장 공간(소유 영토 + 홈 아일랜드) 식량 총합. 섬·영토 OR은 LEFT JOIN 필수(위 주석 참고). */
    @Query(
            "SELECT COALESCE(SUM(b.storedFood), 0) FROM BuildingInstance b"
                    + " LEFT JOIN b.island isl"
                    + " WHERE b.buildingType.name IN ('STORAGE', 'CASTLE') AND b.posX >= 0"
                    + " AND (isl.userId = :userId OR b.territoryId IN :territoryIds)")
    int sumStoredFoodByOwnerId(
            @Param("userId") Long userId, @Param("territoryIds") List<Long> territoryIds);

    @Query(
            "SELECT COUNT(b) > 0 FROM BuildingInstance b"
                    + " WHERE b.island.id = :islandId AND b.buildingType.name = 'CASTLE'")
    boolean existsCastleOnIsland(@Param("islandId") Long islandId);

    @Query(
            "SELECT COUNT(b) > 0 FROM BuildingInstance b"
                    + " WHERE b.territoryId = :territoryId AND b.buildingType.name = 'CASTLE'")
    boolean existsCastleOnTerritory(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT b.level FROM BuildingInstance b"
                    + " WHERE b.island.id = :islandId AND b.buildingType.name = 'CASTLE'")
    Optional<Integer> findCastleLevelByIslandId(@Param("islandId") Long islandId);

    /** 섬에 배치된 특정 종류의 건물 수 — 건설 중인 것도 자리를 차지하므로 함께 센다. */
    @Query(
            "SELECT COUNT(b) FROM BuildingInstance b"
                    + " WHERE b.island.id = :islandId AND b.buildingType.id = :buildingTypeId")
    long countByIslandIdAndBuildingTypeId(
            @Param("islandId") Long islandId, @Param("buildingTypeId") Long buildingTypeId);

    @Query(
            "SELECT COUNT(b) FROM BuildingInstance b"
                    + " WHERE b.territoryId = :territoryId AND b.buildingType.id = :buildingTypeId")
    long countByTerritoryIdAndBuildingTypeId(
            @Param("territoryId") Long territoryId, @Param("buildingTypeId") Long buildingTypeId);

    @Query(
            "SELECT b FROM BuildingInstance b JOIN FETCH b.buildingType"
                    + " WHERE b.island IS NOT NULL AND b.buildingType.name = 'CASTLE'"
                    + " AND b.posX = :posX AND b.posY = :posY")
    List<BuildingInstance> findIslandCastlesAtPosition(
            @Param("posX") int posX, @Param("posY") int posY);
}
