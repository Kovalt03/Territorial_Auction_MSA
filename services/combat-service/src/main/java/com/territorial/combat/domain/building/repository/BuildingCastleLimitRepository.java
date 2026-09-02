package com.territorial.combat.domain.building.repository;

import com.territorial.combat.domain.building.entity.BuildingCastleLimit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingCastleLimitRepository extends JpaRepository<BuildingCastleLimit, Long> {

    List<BuildingCastleLimit> findAllByBuildingType_Id(Long buildingTypeId);

    Optional<BuildingCastleLimit> findByBuildingType_IdAndCastleLevel(
            Long buildingTypeId, int castleLevel);
}
