package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.LandTaxLog;
import com.territorial.map.domain.map.entity.LandTaxLog.TaxStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandTaxLogRepository extends JpaRepository<LandTaxLog, Long> {

    Page<LandTaxLog> findByUserIdOrderByChargedAtDesc(Long userId, Pageable pageable);

    Page<LandTaxLog> findByUserIdAndStatusOrderByChargedAtDesc(
            Long userId, TaxStatus status, Pageable pageable);

    boolean existsByUserIdAndStatusAndChargedAtAfter(
            Long userId, TaxStatus status, LocalDateTime after);
}
