CREATE TABLE combat_event_receipts (
    receipt_key  VARCHAR(200) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
