package com.territorial.auction.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NotificationSetting {

    @Id private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean isOutbidEnabled = true;

    @Column(nullable = false)
    private boolean isAuctionStartEnabled = true;

    @Column(nullable = false)
    private boolean isMarketingEnabled = false;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public NotificationSetting(User user) {
        this.user = user;
    }

    public void update(
            Boolean isOutbidEnabled, Boolean isAuctionStartEnabled, Boolean isMarketingEnabled) {
        if (isOutbidEnabled != null) this.isOutbidEnabled = isOutbidEnabled;
        if (isAuctionStartEnabled != null) this.isAuctionStartEnabled = isAuctionStartEnabled;
        if (isMarketingEnabled != null) this.isMarketingEnabled = isMarketingEnabled;
        this.updatedAt = LocalDateTime.now();
    }
}
