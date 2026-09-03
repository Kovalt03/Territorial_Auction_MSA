package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.SeasonPassLevelReward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonPassLevelRewardRepository
        extends JpaRepository<SeasonPassLevelReward, Long> {

    List<SeasonPassLevelReward> findBySeason_IdOrderByLevelAsc(Long seasonId);

    boolean existsBySeason_Id(Long seasonId);
}
