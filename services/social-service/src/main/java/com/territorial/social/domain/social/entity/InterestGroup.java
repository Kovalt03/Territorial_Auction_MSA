package com.territorial.social.domain.social.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "interest_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // continent는 map(공유 커널) 소유 — continentId만 저장.
    @Column(name = "continent_id", nullable = false)
    private Long continentId;

    @Builder
    public InterestGroup(Long userId, Long continentId) {
        this.userId = userId;
        this.continentId = continentId;
    }
}
