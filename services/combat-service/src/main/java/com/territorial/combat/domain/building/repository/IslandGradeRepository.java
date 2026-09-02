package com.territorial.combat.domain.building.repository;

import com.territorial.combat.domain.building.entity.IslandGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IslandGradeRepository extends JpaRepository<IslandGrade, Long> {

    Optional<IslandGrade> findByName(String name);

    Optional<IslandGrade> findByCastleLevelRequired(int castleLevelRequired);
}
