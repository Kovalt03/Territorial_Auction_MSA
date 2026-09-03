package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.UserTrophy;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTrophyRepository extends JpaRepository<UserTrophy, Long> {

    // nickname은 user_display 프로젝션에서 조회측이 붙인다(트로피는 userId 스칼라만 보유).
    Page<UserTrophy> findAllByOrderByScoreDesc(Pageable pageable);

    @Query(
            "SELECT ut FROM UserTrophy ut "
                    + "WHERE ut.score >= :min AND ut.score < :max ORDER BY ut.score DESC")
    List<UserTrophy> findInScoreBandOrderByScoreDesc(
            @Param("min") int min, @Param("max") int max, Pageable pageable);

    long countByScoreGreaterThan(int score);

    long countByScoreGreaterThanAndScoreLessThan(int score, int max);

    @Query("SELECT COALESCE(SUM(ut.score), 0L) FROM UserTrophy ut WHERE ut.userId IN :userIds")
    long sumScoreByUserIdIn(@Param("userIds") List<Long> userIds);

    @Query(
            "SELECT ut.userId, COALESCE(SUM(ut.score), 0L) FROM UserTrophy ut WHERE ut.userId IN :userIds GROUP BY ut.userId")
    List<Object[]> sumScoreGroupByUserIds(@Param("userIds") List<Long> userIds);

    List<UserTrophy> findAllBySeasonId(Long seasonId);
}
