package com.territorial.combat.domain.military.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attack_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttackToken {

    @Id private Long userId;

    @Column(nullable = false)
    private Integer normalCount = 0;

    @Column(nullable = false)
    private Integer precisionCount = 0;

    @Builder
    public AttackToken(Long userId) {
        this.userId = userId;
    }

    public void addNormal() {
        normalCount++;
    }

    public void addNormal(int count) {
        normalCount += count;
    }

    public void addPrecision() {
        precisionCount++;
    }

    public void addPrecision(int count) {
        precisionCount += count;
    }

    public void consumeNormal() {
        normalCount--;
    }

    public void consumePrecision() {
        precisionCount--;
    }
}
