package com.territorial.auction.domain.building.repository;

import com.territorial.auction.domain.building.entity.BuildingLevelSpec;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingLevelSpecRepository extends JpaRepository<BuildingLevelSpec, Long> {

    List<BuildingLevelSpec> findAllByBuildingType_Id(Long buildingTypeId);

    List<BuildingLevelSpec> findAllByBuildingType_IdIn(Collection<Long> buildingTypeIds);

    Optional<BuildingLevelSpec> findByBuildingType_IdAndLevel(Long buildingTypeId, Integer level);
}
