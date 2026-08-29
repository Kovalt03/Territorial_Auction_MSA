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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Builder
    public Wallet(User user) {
        this.user = user;
    }

    public void lockAp(int amount) {
        availableAp -= amount;
        lockedAp += amount;
    }

    public void refundLockedAp(int amount) {
        lockedAp -= amount;
        availableAp += amount;
    }

    public void consumeLockedAp(int amount) {
        if (lockedAp < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
        lockedAp -= amount;
    }
}
