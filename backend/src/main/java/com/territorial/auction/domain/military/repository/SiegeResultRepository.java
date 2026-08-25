package com.territorial.auction.domain.military.repository;

import com.territorial.auction.domain.military.entity.SiegeResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiegeResultRepository extends JpaRepository<SiegeResult, Long> {

    Optional<SiegeResult> findBySiegeId(Long siegeId);
}
