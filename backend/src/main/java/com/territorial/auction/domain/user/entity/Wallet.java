package com.territorial.auction.domain.user.entity;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Wallet {

    @Id private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer availableAp = 0;

    @Column(nullable = false)
    private Integer lockedAp = 0;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Wallet(User user) {
        this.user = user;
    }

    public void lockAp(int amount) {
        this.availableAp -= amount;
        this.lockedAp += amount;
    }

    public void refundLockedAp(int amount) {
        this.lockedAp -= amount;
        this.availableAp += amount;
    }

    public void consumeLockedAp(int amount) {
        this.lockedAp -= amount;
    }

    public void spendAp(int amount) {
        this.availableAp -= amount;
    }

    public void addAp(int amount) {
        this.availableAp += amount;
    }

    // 관리자 재화 조정: delta는 증감 모두 허용, 결과가 음수면 거부한다.
    public void adjustAvailableAp(int delta) {
        if (this.availableAp + delta < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
        this.availableAp += delta;
    }
}
