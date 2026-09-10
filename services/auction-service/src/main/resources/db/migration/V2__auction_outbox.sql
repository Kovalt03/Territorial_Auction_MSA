-- 트랜잭셔널 아웃박스: auction 도메인 이벤트를 입찰/정산 트랜잭션과 원자적으로 기록하고,
-- 별도 릴레이(@Scheduled)가 Kafka(auction-events)로 드레인한다. Kafka 장애 시에도 유실 없이 복구 후 전달.
CREATE TABLE auction_outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_auction_outbox_pending
    ON auction_outbox_events (created_at)
    WHERE published_at IS NULL;
