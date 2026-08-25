package com.territorial.auction.domain.item.repository;

import com.territorial.auction.domain.item.entity.ItemPurchase;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemPurchaseRepository extends JpaRepository<ItemPurchase, Long> {

    @Query(
            "SELECT COALESCE(SUM(p.quantity), 0) FROM ItemPurchase p"
                    + " WHERE p.user.id = :userId AND p.item.id = :itemId"
                    + " AND p.purchasedAt >= :startOfDay")
    int sumTodayQuantity(
            @Param("userId") Long userId,
            @Param("itemId") Long itemId,
            @Param("startOfDay") LocalDateTime startOfDay);
}
