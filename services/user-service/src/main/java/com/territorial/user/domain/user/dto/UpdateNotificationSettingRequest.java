package com.territorial.user.domain.user.dto;

public record UpdateNotificationSettingRequest(
        Boolean isOutbidEnabled, Boolean isAuctionStartEnabled, Boolean isMarketingEnabled) {}
