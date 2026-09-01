package com.territorial.user.domain.user.entity;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Wallet(User user) {
        this.user = user;
    }

    public void lockAp(int amount) {
        validatePositive(amount);
        availableAp -= amount;
        lockedAp += amount;
    }

    public void refundLockedAp(int amount) {
        validateLockedAmount(amount);
        lockedAp -= amount;
        availableAp += amount;
    }

    public void consumeLockedAp(int amount) {
        validateLockedAmount(amount);
        lockedAp -= amount;
    }

    public void addAp(int amount) {
        validatePositive(amount);
        availableAp += amount;
    }

    /** 가용 AP 차감(소비). 잔액 부족 시 거부. */
    public void spendAp(int amount) {
        validatePositive(amount);
        if (availableAp < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
        availableAp -= amount;
    }

    /** 관리자 재화 조정: delta는 증감 모두 허용, 결과가 음수면 거부. */
    public void adjustAvailableAp(int delta) {
        if (availableAp + delta < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
        availableAp += delta;
    }

    private void validateLockedAmount(int amount) {
        validatePositive(amount);
        if (lockedAp < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
    }

    private void validatePositive(int amount) {
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_WALLET_AMOUNT);
        }
    }
}
