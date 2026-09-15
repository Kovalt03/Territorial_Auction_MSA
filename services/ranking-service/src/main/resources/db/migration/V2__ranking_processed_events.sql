-- 이벤트 소비 멱등 기록. auction.settled 처리(XP 전송·영토 보유 INSERT·경매소비 ZSet 증분)는
-- 비멱등이라, Kafka at-least-once 재전달 시 event_key(예: AUCTION_SETTLED:{auctionId})로 중복 처리를 막는다.
CREATE TABLE ranking_processed_events (
    event_key   VARCHAR(200) PRIMARY KEY,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
