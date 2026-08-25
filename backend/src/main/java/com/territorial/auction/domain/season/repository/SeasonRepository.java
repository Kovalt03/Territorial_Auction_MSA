package com.territorial.auction.domain.season.repository;

import com.territorial.auction.domain.season.entity.Season;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    boolean existsBySeasonNumber(Integer seasonNumber);

    List<Season> findAllByOrderBySeasonNumberDesc();

    @Query("SELECT COALESCE(MAX(s.seasonNumber), 0) FROM Season s")
    int findMaxSeasonNumber();

    @Query(
            "SELECT s FROM Season s WHERE s.startedAt <= :now AND (s.endedAt IS NULL OR s.endedAt >= :now)")
    Optional<Season> findActiveSeason(@Param("now") LocalDateTime now);

    @Query(
            "SELECT s FROM Season s WHERE s.endedAt IS NOT NULL AND s.endedAt < :now AND s.processedAt IS NULL ORDER BY s.endedAt ASC LIMIT 1")
    Optional<Season> findFirstUnprocessedEndedSeason(@Param("now") LocalDateTime now);
}
