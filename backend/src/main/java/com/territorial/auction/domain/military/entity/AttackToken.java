package com.territorial.auction.domain.military.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attack_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttackToken {

    @Id private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer normalCount = 0;

    @Column(nullable = false)
    private Integer precisionCount = 0;

    @Builder
    public AttackToken(User user) {
        this.user = user;
    }

    public void addNormal() {
        this.normalCount++;
    }

    public void addNormal(int count) {
        this.normalCount += count;
    }

    public void addPrecision() {
        this.precisionCount++;
    }

    public void addPrecision(int count) {
        this.precisionCount += count;
    }

    public void consumeNormal() {
        this.normalCount--;
    }

    public void consumePrecision() {
        this.precisionCount--;
    }
}
