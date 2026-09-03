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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// user_id는 user-service 소유 식별자 — FK 없이 Long으로 보관. item_id는 서비스 내부 FK.
@Entity
@Table(
        name = "user_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserItem(Long userId, Item item, int quantity, LocalDateTime createdAt) {
        this.userId = userId;
        this.item = item;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public void add(int amount) {
        this.quantity += amount;
    }

    public void use() {
        this.quantity -= 1;
    }
}
