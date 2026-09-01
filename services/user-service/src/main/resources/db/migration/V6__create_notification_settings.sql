CREATE TABLE notification_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    is_outbid_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_auction_start_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_marketing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
