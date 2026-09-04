package com.territorial.user.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** AP 충전 주문 멱등 기록 — orderId 중복 충전을 거부한다(기존 모놀 Redis 대체, DB 영속). */
@Entity
@Table(name = "payment_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder {

    @Id
    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PaymentOrder(String orderId, Long userId, int amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }
}
