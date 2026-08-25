package com.territorial.auction.domain.military.repository;

import com.territorial.auction.domain.military.entity.SiegeForce;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiegeForceRepository extends JpaRepository<SiegeForce, Long> {

    List<SiegeForce> findBySiegeId(Long siegeId);
}
