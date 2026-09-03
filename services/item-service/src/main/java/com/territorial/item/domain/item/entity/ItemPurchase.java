package com.territorial.item.domain.item.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// user_id·target_territory_id는 외부 서비스 소유 식별자 — FK 없이 Long 스칼라. item_id는 내부 FK.
@Entity
@Table(name = "item_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "target_territory_id")
    private Long targetTerritoryId; // NULL 허용, 무적권 사용 시 대상 영토

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @Builder
    public ItemPurchase(
            Long userId,
            Item item,
            Integer quantity,
            Long targetTerritoryId,
            LocalDateTime purchasedAt) {
        this.userId = userId;
        this.item = item;
        this.quantity = quantity != null ? quantity : 1;
        this.targetTerritoryId = targetTerritoryId;
        this.purchasedAt = purchasedAt;
    }
}
