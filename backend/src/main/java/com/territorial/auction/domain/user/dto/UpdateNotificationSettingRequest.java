package com.territorial.auction.domain.user.dto;

public record UpdateNotificationSettingRequest(
        Boolean isOutbidEnabled, Boolean isAuctionStartEnabled, Boolean isMarketingEnabled) {}
