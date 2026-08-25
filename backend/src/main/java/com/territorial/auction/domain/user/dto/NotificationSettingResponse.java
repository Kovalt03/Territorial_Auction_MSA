package com.territorial.auction.domain.user.dto;

import com.territorial.auction.domain.user.entity.NotificationSetting;
import java.time.LocalDateTime;

public record NotificationSettingResponse(
        boolean isOutbidEnabled,
        boolean isAuctionStartEnabled,
        boolean isMarketingEnabled,
        LocalDateTime updatedAt) {

    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.isOutbidEnabled(),
                setting.isAuctionStartEnabled(),
                setting.isMarketingEnabled(),
                setting.getUpdatedAt());
    }
}
