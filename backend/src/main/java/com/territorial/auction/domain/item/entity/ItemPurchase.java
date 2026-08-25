package com.territorial.auction.domain.item.entity;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "item_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_territory_id")
    private Territory targetTerritory; // NULL 허용, 무적권 사용 시 대상 영토

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @Builder
    public ItemPurchase(
            User user,
            Item item,
            Integer quantity,
            Territory targetTerritory,
            LocalDateTime purchasedAt) {
        this.user = user;
        this.item = item;
        this.quantity = quantity != null ? quantity : 1;
        this.targetTerritory = targetTerritory;
        this.purchasedAt = purchasedAt;
    }
}
