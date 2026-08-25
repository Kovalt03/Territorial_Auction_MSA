package com.territorial.auction.domain.season.repository;

import com.territorial.auction.domain.season.entity.UserTrophy;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTrophyRepository extends JpaRepository<UserTrophy, Long> {

    @EntityGraph(attributePaths = "user")
    Page<UserTrophy> findAllByOrderByScoreDesc(Pageable pageable);

    @Query(
            "SELECT ut FROM UserTrophy ut JOIN FETCH ut.user "
                    + "WHERE ut.score >= :min AND ut.score < :max ORDER BY ut.score DESC")
    List<UserTrophy> findInScoreBandOrderByScoreDesc(
            @Param("min") int min, @Param("max") int max, Pageable pageable);

    long countByScoreGreaterThan(int score);

    long countByScoreGreaterThanAndScoreLessThan(int score, int max);

    @Query("SELECT COALESCE(SUM(ut.score), 0L) FROM UserTrophy ut WHERE ut.user.id IN :userIds")
    long sumScoreByUserIdIn(@Param("userIds") List<Long> userIds);

    @Query(
            "SELECT ut.user.id, COALESCE(SUM(ut.score), 0L) FROM UserTrophy ut WHERE ut.user.id IN :userIds GROUP BY ut.user.id")
    List<Object[]> sumScoreGroupByUserIds(@Param("userIds") List<Long> userIds);

    List<UserTrophy> findAllBySeasonId(Long seasonId);
}
