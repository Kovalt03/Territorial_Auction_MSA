package com.territorial.auction.domain.guild.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_id", nullable = false)
    private User master;

    @Column(nullable = false)
    private int maxMembers = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private RecruitingStatus recruitingStatus = RecruitingStatus.OPEN;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Guild(String name, String description, String emblem, User master) {
        this.name = name;
        this.description = description;
        this.emblem = emblem;
        this.master = master;
    }

    public void transferMaster(User newMaster) {
        this.master = newMaster;
    }

    public void updateInfo(String description, String emblem, RecruitingStatus recruitingStatus) {
        if (description != null) this.description = description;
        if (emblem != null) this.emblem = emblem;
        if (recruitingStatus != null) this.recruitingStatus = recruitingStatus;
    }
}
