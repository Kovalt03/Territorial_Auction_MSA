package com.territorial.user.domain.user.entity;

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
@Table(name = "global_vaults")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlobalVault {

    @Id private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer storedGp = 0;

    @Column(nullable = false)
    private Integer capacity = 10_000;

    @Builder
    public GlobalVault(User user) {
        this.user = user;
    }
}
