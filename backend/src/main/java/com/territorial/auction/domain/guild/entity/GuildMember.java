package com.territorial.auction.domain.guild.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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
    public GuildMember(Guild guild, User user, Role role, Status status, String message) {
        this.guild = guild;
        this.user = user;
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
