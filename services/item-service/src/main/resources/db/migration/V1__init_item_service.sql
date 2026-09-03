-- item-service 초기 스키마. user_id·target_territory_id는 외부 서비스 소유 식별자(FK 없음).
CREATE TABLE items (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL,
    item_type    VARCHAR(20)  NOT NULL,
    description  VARCHAR(200),
    cost_ap      INTEGER,
    cost_gp      INTEGER,
    daily_limit  INTEGER,
    gp_reward    INTEGER,
    icon_url     VARCHAR(100)
);

CREATE TABLE user_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    item_id    BIGINT    NOT NULL REFERENCES items (id),
    quantity   INTEGER   NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_user_items_user ON user_items (user_id);

CREATE TABLE item_purchases (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT    NOT NULL,
    item_id             BIGINT    NOT NULL REFERENCES items (id),
    quantity            INTEGER   NOT NULL DEFAULT 1,
    target_territory_id BIGINT,
    purchased_at        TIMESTAMP NOT NULL
);

CREATE INDEX idx_item_purchases_daily ON item_purchases (user_id, item_id, purchased_at);
