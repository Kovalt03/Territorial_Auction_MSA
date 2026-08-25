package com.territorial.auction.domain.military.repository;

import com.territorial.auction.domain.military.entity.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitTypeRepository extends JpaRepository<UnitType, Long> {

    java.util.Optional<UnitType> findByName(String name);
}
