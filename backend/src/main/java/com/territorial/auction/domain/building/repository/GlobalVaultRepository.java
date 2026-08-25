package com.territorial.auction.domain.building.repository;

import com.territorial.auction.domain.building.entity.GlobalVault;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlobalVaultRepository extends JpaRepository<GlobalVault, Long> {

    // 금고 GP는 여러 경로(연구·공성건물·약탈·토지세·아이템·시즌·이전)에서 read-modify-write 된다.
    // 갱신 유실을 막기 위해 쓰기 경로는 반드시 이 락 조회를 쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM GlobalVault v WHERE v.userId = :userId")
    Optional<GlobalVault> findByIdWithLock(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(v.storedGp), 0) FROM GlobalVault v")
    long sumStoredGp();
}
