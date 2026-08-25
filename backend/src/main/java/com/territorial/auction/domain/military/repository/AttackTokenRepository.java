package com.territorial.auction.domain.military.repository;

import com.territorial.auction.domain.military.entity.AttackToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttackTokenRepository extends JpaRepository<AttackToken, Long> {

    Optional<AttackToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AttackToken a WHERE a.user.id = :userId")
    Optional<AttackToken> findByUserIdWithLock(@Param("userId") Long userId);
}
