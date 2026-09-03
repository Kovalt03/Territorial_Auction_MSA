package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.SeasonPass;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonPassRepository extends JpaRepository<SeasonPass, Long> {

    java.util.Optional<SeasonPass> findByName(String name);

    Optional<SeasonPass> findFirstByOrderByIdDesc();
}
