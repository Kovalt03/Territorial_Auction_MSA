package com.territorial.auction.domain.building.repository;

import com.territorial.auction.domain.building.entity.BuildingType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingTypeRepository extends JpaRepository<BuildingType, Long> {
    Optional<BuildingType> findByName(String name);

    boolean existsByName(String name);
}
