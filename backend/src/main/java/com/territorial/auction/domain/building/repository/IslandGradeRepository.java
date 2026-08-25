package com.territorial.auction.domain.building.repository;

import com.territorial.auction.domain.building.entity.IslandGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IslandGradeRepository extends JpaRepository<IslandGrade, Long> {

    Optional<IslandGrade> findByName(String name);

    Optional<IslandGrade> findByCastleLevelRequired(int castleLevelRequired);
}
