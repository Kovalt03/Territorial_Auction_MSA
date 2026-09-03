package com.territorial.combat.domain.building.repository;

import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CombatUserSnapshotRepository extends JpaRepository<CombatUserSnapshot, Long> {}
