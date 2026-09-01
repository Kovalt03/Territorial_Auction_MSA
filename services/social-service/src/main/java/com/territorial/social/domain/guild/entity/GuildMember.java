package com.territorial.social.domain.guild.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guild_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuildMember {

    public enum Role {
        MASTER,
        OFFICER,
        MEMBER
    }

    public enum Status {
        PENDING,
        ACTIVE,
        INACTIVE,
        KICKED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    private Guild guild;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(length = 200)
    private String message;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Builder
    public GuildMember(Guild guild, Long userId, Role role, Status status, String message) {
        this.guild = guild;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.message = message;
        this.joinedAt = LocalDateTime.now();
    }

    public void approve() {
        this.status = Status.ACTIVE;
    }

    public void leave() {
        this.status = Status.INACTIVE;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public void kick() {
        this.status = Status.KICKED;
    }

    public void promoteToMaster() {
        this.role = Role.MASTER;
    }

    public void demoteToMember() {
        this.role = Role.MEMBER;
    }
}
