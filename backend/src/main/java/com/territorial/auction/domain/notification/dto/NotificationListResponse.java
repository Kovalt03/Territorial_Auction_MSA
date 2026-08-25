package com.territorial.auction.domain.notification.dto;

import java.util.List;

public record NotificationListResponse(
        long unreadCount, List<NotificationResponse> notifications) {}
