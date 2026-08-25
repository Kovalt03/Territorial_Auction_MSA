package com.territorial.auction.domain.notification.entity;

import com.territorial.auction.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private boolean isRead = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public NotificationLog(User user, NotificationType type, String message) {
        this.user = user;
        this.type = type;
        this.message = message;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public enum NotificationType {
        OUTBID,
        AUCTION_WIN,
        AUCTION_LOSE,
        SIEGE_ALERT,
        SIEGE_RESULT,
        TAX_CHARGED,
        INCOME,
        SEASON_PASS_EXPIRING,
        TAX_FAIL_WARNING,
        TAX_EVICTION,
        ISLAND_EXPANDED,
        ADMIN_NOTICE
    }
}
