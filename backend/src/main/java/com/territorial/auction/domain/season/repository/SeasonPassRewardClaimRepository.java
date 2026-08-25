package com.territorial.auction.domain.season.repository;

import com.territorial.auction.domain.season.entity.SeasonPassRewardClaim;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonPassRewardClaimRepository
        extends JpaRepository<SeasonPassRewardClaim, Long> {

    @Query(
            "SELECT c.reward.id FROM SeasonPassRewardClaim c"
                    + " WHERE c.user.id = :userId AND c.reward.season.id = :seasonId")
    Set<Long> findClaimedRewardIdsByUserIdAndSeasonId(
            @Param("userId") Long userId, @Param("seasonId") Long seasonId);

    boolean existsByUser_IdAndReward_Id(Long userId, Long rewardId);
}
