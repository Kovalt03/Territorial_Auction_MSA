package com.territorial.auction.domain.notification;

// 알림 저장·조회는 notification-service가 소유한다. 모놀리식은 각 도메인이 발행할 때 쓰는 타입만 보유.
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
