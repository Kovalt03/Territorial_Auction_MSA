package com.territorial.auction.domain.map.repository;

import com.territorial.auction.domain.map.entity.TerritoryGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryGradeRepository extends JpaRepository<TerritoryGrade, Long> {

    Optional<TerritoryGrade> findByGrade(String grade);
}
