package com.territorial.combat.domain.building.repository;

import com.territorial.combat.domain.building.entity.HomeIsland;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeIslandRepository extends JpaRepository<HomeIsland, Long> {

    Optional<HomeIsland> findByUserId(Long userId);

    List<HomeIsland> findByGridSize(int gridSize);
}
