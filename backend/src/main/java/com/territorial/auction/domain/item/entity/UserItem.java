package com.territorial.auction.domain.item.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserItem(User user, Item item, int quantity, LocalDateTime createdAt) {
        this.user = user;
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
