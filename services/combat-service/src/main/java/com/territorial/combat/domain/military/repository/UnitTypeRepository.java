package com.territorial.combat.domain.military.repository;

import com.territorial.combat.domain.military.entity.UnitType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitTypeRepository extends JpaRepository<UnitType, Long> {
    Optional<UnitType> findByName(String name);
}
