-- combat-events 소비 멱등: SIEGE_WIN(XP·미션) 중복 처리 방지.
CREATE TABLE processed_combat_events (
    receipt_key VARCHAR(200) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
