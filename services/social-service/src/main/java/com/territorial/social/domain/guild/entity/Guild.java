package com.territorial.social.domain.guild.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "guilds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Guild {

    public enum RecruitingStatus {
        OPEN,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(length = 255)
    private String emblem;

    // 신원은 user-service 소유 — masterId만 저장.
    @Column(name = "master_id", nullable = false)
    private Long masterId;

    @Column(nullable = false)
    private int maxMembers = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private RecruitingStatus recruitingStatus = RecruitingStatus.OPEN;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Guild(String name, String description, String emblem, Long masterId) {
        this.name = name;
        this.description = description;
        this.emblem = emblem;
        this.masterId = masterId;
    }

    public void transferMaster(Long newMasterId) {
        this.masterId = newMasterId;
    }

    public void updateInfo(String description, String emblem, RecruitingStatus recruitingStatus) {
        if (description != null) this.description = description;
        if (emblem != null) this.emblem = emblem;
        if (recruitingStatus != null) this.recruitingStatus = recruitingStatus;
    }
}
