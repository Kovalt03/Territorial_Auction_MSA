package com.territorial.user.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @UpdateTimestamp
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
    }
}
