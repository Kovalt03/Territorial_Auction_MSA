package com.territorial.season.domain.season.repository;

import com.territorial.season.domain.season.entity.UserSeasonPass;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSeasonPassRepository extends JpaRepository<UserSeasonPass, Long> {

    Optional<UserSeasonPass> findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(Long userId);

    List<UserSeasonPass> findByIsActiveTrueAndExpiresAtBetween(
            LocalDateTime from, LocalDateTime to);

    // 시즌 종료 시 전 유저 패스 일괄 비활성화
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserSeasonPass u SET u.isActive = false WHERE u.isActive = true")
    int deactivateAllActive();
}
