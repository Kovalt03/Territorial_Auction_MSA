package com.territorial.auction.domain.season.repository;

import com.territorial.auction.domain.season.entity.SeasonReward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRewardRepository extends JpaRepository<SeasonReward, Long> {

    boolean existsBySeasonIdAndUserId(Long seasonId, Long userId);
}
