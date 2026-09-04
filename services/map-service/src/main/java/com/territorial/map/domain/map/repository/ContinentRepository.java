package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.Continent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContinentRepository extends JpaRepository<Continent, Long> {
    boolean existsByDisplayNameIsNull();

    @Query("SELECT MIN(c.minTrophyRequired) FROM Continent c WHERE c.minTrophyRequired > :lower")
    Integer findNextMinTrophyAbove(@Param("lower") int lower);
}
