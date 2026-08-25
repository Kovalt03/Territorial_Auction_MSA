package com.territorial.auction.domain.season.entity;

import com.territorial.auction.domain.military.entity.SiegeEvent;
import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "trophy_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TrophyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siege_id")
    private SiegeEvent siege; // NULL 허용

    @Column(nullable = false)
    private Integer delta; // 변동량 (+/-)

    @Column(nullable = false, length = 30)
    private String reason; // ATK_WIN_CASTLE / ATK_FAIL / DEF_WIN 등

    @Column(nullable = false)
    private Integer scoreAfter;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public TrophyLog(
            User user,
            Season season,
            SiegeEvent siege,
            Integer delta,
            String reason,
            Integer scoreAfter) {
        this.user = user;
        this.season = season;
        this.siege = siege;
        this.delta = delta;
        this.reason = reason;
        this.scoreAfter = scoreAfter;
    }
}
