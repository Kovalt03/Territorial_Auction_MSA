package com.territorial.item.domain.item.repository;

import com.territorial.item.domain.item.entity.ItemPurchase;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemPurchaseRepository extends JpaRepository<ItemPurchase, Long> {

    @Query(
            "SELECT COALESCE(SUM(p.quantity), 0) FROM ItemPurchase p"
                    + " WHERE p.userId = :userId AND p.item.id = :itemId"
                    + " AND p.purchasedAt >= :startOfDay")
    int sumTodayQuantity(
            @Param("userId") Long userId,
            @Param("itemId") Long itemId,
            @Param("startOfDay") LocalDateTime startOfDay);

    // 일일 한도 검사(read)→구매(write)를 유저 단위로 직렬화한다. 트랜잭션 종료 시 자동 해제되며,
    // userId를 bigint 키로 그대로 써 int4 오버플로·키 충돌이 없다(이 서비스의 유일한 advisory lock).
    @Query(value = "SELECT pg_advisory_xact_lock(:userId)", nativeQuery = true)
    void acquireDailyLimitLock(@Param("userId") Long userId);
}
