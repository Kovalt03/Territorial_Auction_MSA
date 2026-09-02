package com.territorial.combat.domain.building.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "global_vaults")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlobalVault {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private Integer storedGp = 0;

    // 위치 저장 총량(성 5,000 + 저장소 Lv3 15,000 = 20,000) 대비 의미 있는 규모.
    @Column(nullable = false)
    private Integer capacity = 10_000;

    private LocalDateTime lastTransferAt;

    @Builder
    public GlobalVault(Long userId) {
        this.userId = userId;
    }

    public void receiveGp(int amount) {
        this.storedGp += amount;
    }

    public void withdrawGp(int amount) {
        this.storedGp -= amount;
    }

    public void recordTransfer() {
        this.lastTransferAt = java.time.LocalDateTime.now();
    }
}
