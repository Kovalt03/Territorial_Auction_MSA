package com.territorial.auction.domain.season.repository;

import com.territorial.auction.domain.season.entity.SeasonPassProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonPassProgressRepository extends JpaRepository<SeasonPassProgress, Long> {

    Optional<SeasonPassProgress> findByUser_IdAndSeason_Id(Long userId, Long seasonId);
}
