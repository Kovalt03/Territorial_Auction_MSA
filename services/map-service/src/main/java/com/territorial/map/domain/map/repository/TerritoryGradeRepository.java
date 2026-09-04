package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.TerritoryGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryGradeRepository extends JpaRepository<TerritoryGrade, Long> {

    Optional<TerritoryGrade> findByGrade(String grade);
}
