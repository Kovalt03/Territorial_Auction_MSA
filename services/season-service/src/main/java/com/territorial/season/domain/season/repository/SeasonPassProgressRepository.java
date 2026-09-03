package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.SeasonPassProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonPassProgressRepository extends JpaRepository<SeasonPassProgress, Long> {

    Optional<SeasonPassProgress> findByUserIdAndSeason_Id(Long userId, Long seasonId);
}
