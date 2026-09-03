package com.territorial.ranking.domain.ranking.repository;

import com.territorial.ranking.domain.ranking.entity.SeasonTerritoryHold;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonTerritoryHoldRepository extends JpaRepository<SeasonTerritoryHold, Long> {

    List<SeasonTerritoryHold> findAllBySeasonId(Long seasonId);

    Optional<SeasonTerritoryHold> findBySeasonIdAndUserIdAndTerritoryIdAndHeldUntilIsNull(
            Long seasonId, Long userId, Long territoryId);

    List<SeasonTerritoryHold> findBySeasonIdAndUserId(Long seasonId, Long userId);
}
