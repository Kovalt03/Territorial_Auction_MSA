CREATE TABLE user_outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_user_outbox_pending
    ON user_outbox_events (created_at)
    WHERE published_at IS NULL;
