-- combat-events 소비 멱등: 공성·섬 알림 저장 중복 방지.
CREATE TABLE processed_combat_events (
    receipt_key VARCHAR(200) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
