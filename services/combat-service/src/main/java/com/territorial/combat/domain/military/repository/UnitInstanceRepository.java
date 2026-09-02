package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.UnitInstance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitInstanceRepository extends JpaRepository<UnitInstance, Long> {

    List<UnitInstance> findByUserId(Long userId);

    List<UnitInstance> findByUserIdAndDeployedTerritoryId(Long userId, Long territoryId);

    @Query(
            "SELECT u FROM UnitInstance u WHERE u.userId = :userId"
                    + " AND (u.homeTerritoryId = :territoryId OR u.deployedTerritoryId = :territoryId)")
    List<UnitInstance> findByOwnerAndTerritoryAssociation(
            @Param("userId") Long userId, @Param("territoryId") Long territoryId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeTerritoryId = :homeTerritoryId"
                    + " AND u.deployedTerritoryId IS NULL AND u.moveCompleteAt IS NULL")
    Optional<UnitInstance> findReadyIdleAtTerritory(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeTerritoryId") Long homeTerritoryId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeIsland.id = :homeIslandId"
                    + " AND u.deployedTerritoryId IS NULL AND u.moveCompleteAt IS NULL")
    Optional<UnitInstance> findReadyIdleAtIsland(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeIslandId") Long homeIslandId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeTerritoryId = :homeTerritoryId"
                    + " AND u.deployedBuilding.id = :buildingId")
    Optional<UnitInstance> findDeployedFromTerritory(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeTerritoryId") Long homeTerritoryId,
            @Param("buildingId") Long buildingId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.homeIsland.id = :homeIslandId"
                    + " AND u.deployedBuilding.id = :buildingId")
    Optional<UnitInstance> findDeployedFromIsland(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("homeIslandId") Long homeIslandId,
            @Param("buildingId") Long buildingId);

    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u"
                    + " WHERE u.deployedBuilding.id = :buildingId")
    Integer sumQuantityByDeployedBuildingId(@Param("buildingId") Long buildingId);

    List<UnitInstance> findByDeployedBuildingId(Long deployedBuildingId);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.deployedTerritoryId = :territoryId"
                    + " AND u.deployedBuilding.zone = :zone")
    List<UnitInstance> findDefendersInZone(
            @Param("userId") Long userId,
            @Param("territoryId") Long territoryId,
            @Param("zone") Integer zone);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level AND u.deployedTerritoryId = :territoryId"
                    + " ORDER BY u.id ASC")
    List<UnitInstance> findDeployedAtTerritory(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level,
            @Param("territoryId") Long territoryId);

    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u WHERE u.homeTerritoryId = :territoryId")
    Integer sumQuantityByHomeTerritoryId(@Param("territoryId") Long territoryId);

    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u WHERE u.homeIsland.id = :islandId")
    Integer sumQuantityByHomeIslandId(@Param("islandId") Long islandId);

    @Query(
            "SELECT COALESCE(SUM(u.quantity), 0) FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level"
                    + " AND u.deployedTerritoryId IS NULL AND u.moveCompleteAt IS NULL")
    Integer sumReadyIdleQuantity(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.userId = :userId AND u.unitType.id = :unitTypeId"
                    + " AND u.level = :level"
                    + " AND u.deployedTerritoryId IS NULL AND u.moveCompleteAt IS NULL")
    List<UnitInstance> findReadyIdleByUserIdAndUnitTypeIdAndLevel(
            @Param("userId") Long userId,
            @Param("unitTypeId") Long unitTypeId,
            @Param("level") int level);

    @Query(
            "SELECT u FROM UnitInstance u"
                    + " WHERE u.moveCompleteAt IS NOT NULL AND u.moveCompleteAt <= :now")
    List<UnitInstance> findArrivedInTransit(@Param("now") LocalDateTime now);

    @Query(
            "SELECT u.deployedTerritoryId, SUM(u.quantity) FROM UnitInstance u"
                    + " WHERE u.deployedTerritoryId IN :territoryIds"
                    + " GROUP BY u.deployedTerritoryId")
    List<Object[]> sumQuantityGroupByTerritoryIds(@Param("territoryIds") List<Long> territoryIds);
}
