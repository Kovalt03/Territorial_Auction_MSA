ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_available_ap_nonnegative CHECK (available_ap >= 0),
    ADD CONSTRAINT chk_wallet_locked_ap_nonnegative CHECK (locked_ap >= 0);

CREATE TABLE wallet_commands (
    command_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
