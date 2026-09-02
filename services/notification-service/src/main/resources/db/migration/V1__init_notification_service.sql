-- notification-service 초기 스키마. user_id는 user-service 소유 식별자(FK 없음).
CREATE TABLE notification_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    message    TEXT         NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_notification_user_created ON notification_logs (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification_logs (user_id) WHERE is_read = FALSE;
