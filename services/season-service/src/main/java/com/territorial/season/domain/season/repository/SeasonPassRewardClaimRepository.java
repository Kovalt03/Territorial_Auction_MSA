package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.SeasonPassRewardClaim;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonPassRewardClaimRepository
        extends JpaRepository<SeasonPassRewardClaim, Long> {

    @Query(
            "SELECT c.reward.id FROM SeasonPassRewardClaim c"
                    + " WHERE c.userId = :userId AND c.reward.season.id = :seasonId")
    Set<Long> findClaimedRewardIdsByUserIdAndSeasonId(
            @Param("userId") Long userId, @Param("seasonId") Long seasonId);

    boolean existsByUserIdAndReward_Id(Long userId, Long rewardId);
}
