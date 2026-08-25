-- =============================================
-- Territorial Auction - PostgreSQL Init Script
-- =============================================

-- users
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL     PRIMARY KEY,
    username      VARCHAR(50)   NOT NULL UNIQUE,
    email         VARCHAR(100)  NOT NULL UNIQUE,
    password_hash TEXT          NOT NULL,
    nickname      VARCHAR(30)   NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status        VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    role          VARCHAR(10)   NOT NULL DEFAULT 'USER'
);

-- user_profiles
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id           BIGINT       PRIMARY KEY REFERENCES users(id),
    profile_image_url VARCHAR(255),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

-- wallets
CREATE TABLE IF NOT EXISTS wallets (
    user_id        BIGINT  PRIMARY KEY REFERENCES users(id),
    available_ap   INTEGER NOT NULL DEFAULT 0,
    locked_ap      INTEGER NOT NULL DEFAULT 0,
    available_gp   INTEGER NOT NULL DEFAULT 0,
    available_food INTEGER NOT NULL DEFAULT 100,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- notification_settings
CREATE TABLE IF NOT EXISTS notification_settings (
    user_id                  BIGINT      PRIMARY KEY REFERENCES users(id),
    is_outbid_enabled        BOOLEAN     DEFAULT true,
    is_auction_start_enabled BOOLEAN     DEFAULT true,
    is_marketing_enabled     BOOLEAN     DEFAULT false,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- continents
CREATE TABLE IF NOT EXISTS continents (
    id                  BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(50)  NOT NULL,
    theme_color         VARCHAR(7)   NOT NULL,
    display_name        VARCHAR(50),
    grade               VARCHAR(2),
    min_trophy_required INTEGER,
    description         VARCHAR(100)
);

-- territory_grades
CREATE TABLE IF NOT EXISTS territory_grades (
    id                      BIGSERIAL      PRIMARY KEY,
    grade                   VARCHAR(1)     NOT NULL UNIQUE,
    production_multiplier   NUMERIC(3,1)   NOT NULL,
    auction_price_multiplier NUMERIC(3,1)  NOT NULL,
    pre_built_count         INTEGER        NOT NULL DEFAULT 0,
    spawn_rate              NUMERIC(4,3)   NOT NULL,
    grid_size               INTEGER        NOT NULL,
    zone1_radius            INTEGER        NOT NULL,
    zone2_radius            INTEGER        NOT NULL
);

-- territories
CREATE TABLE IF NOT EXISTS territories (
    id                  BIGSERIAL   PRIMARY KEY,
    coord_x             INTEGER     NOT NULL,
    coord_y             INTEGER     NOT NULL,
    continent_id        BIGINT      REFERENCES continents(id),
    owner_id            BIGINT      REFERENCES users(id),
    current_color       VARCHAR(7),
    occupied_until      TIMESTAMPTZ,
    status              VARCHAR(10) NOT NULL,
    base_production_rate INTEGER    NOT NULL DEFAULT 1,
    auction_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    last_produced_at    TIMESTAMPTZ,
    grade_id            BIGINT      REFERENCES territory_grades(id)
);

-- admin_settings (관리자 전역 설정 key-value)
CREATE TABLE IF NOT EXISTS admin_settings (
    id            BIGSERIAL    PRIMARY KEY,
    setting_key   VARCHAR(50)  NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL
);

-- bonus_tiles
CREATE TABLE IF NOT EXISTS bonus_tiles (
    id           BIGSERIAL     PRIMARY KEY,
    territory_id BIGINT        UNIQUE REFERENCES territories(id),
    multiplier   NUMERIC(4,2)  NOT NULL,
    description  VARCHAR(100)
);

-- auctions
CREATE TABLE IF NOT EXISTS auctions (
    id                BIGSERIAL   PRIMARY KEY,
    territory_id      BIGINT      REFERENCES territories(id),
    current_bidder_id BIGINT      REFERENCES users(id),
    current_price     INTEGER     NOT NULL,
    start_at          TIMESTAMPTZ NOT NULL,
    end_at            TIMESTAMPTZ NOT NULL,
    max_extend_until  TIMESTAMPTZ NOT NULL
);

-- auction_bids
CREATE TABLE IF NOT EXISTS auction_bids (
    id         BIGSERIAL   PRIMARY KEY,
    auction_id BIGINT      REFERENCES auctions(id),
    bidder_id  BIGINT      REFERENCES users(id),
    price      INTEGER     NOT NULL,
    bid_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_auction_bids_auction_bid_at ON auction_bids (auction_id, bid_at ASC);

-- auction_histories
CREATE TABLE IF NOT EXISTS auction_histories (
    id           BIGSERIAL   PRIMARY KEY,
    auction_id   BIGINT      REFERENCES auctions(id),
    territory_id BIGINT      REFERENCES territories(id),
    winner_id    BIGINT      REFERENCES users(id),
    final_price  INTEGER     NOT NULL,
    won_at       TIMESTAMPTZ NOT NULL,
    season_id    BIGINT      -- FK to seasons added below after seasons table
);

-- color_histories
CREATE TABLE IF NOT EXISTS color_histories (
    id           BIGSERIAL   PRIMARY KEY,
    territory_id BIGINT      REFERENCES territories(id),
    user_id      BIGINT      REFERENCES users(id),
    color_code   VARCHAR(7)  NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- territory_production_logs
CREATE TABLE IF NOT EXISTS territory_production_logs (
    id           BIGSERIAL   PRIMARY KEY,
    territory_id BIGINT      REFERENCES territories(id),
    owner_id     BIGINT      REFERENCES users(id),
    amount       INTEGER     NOT NULL,
    reason       VARCHAR(30) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- chat_rooms
CREATE TABLE IF NOT EXISTS chat_rooms (
    id        BIGSERIAL   PRIMARY KEY,
    type      VARCHAR(10) NOT NULL,
    target_id BIGINT
);

-- chat_messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id        BIGSERIAL   PRIMARY KEY,
    room_id   BIGINT      REFERENCES chat_rooms(id),
    sender_id BIGINT      REFERENCES users(id),
    content   TEXT        NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- interest_groups
CREATE TABLE IF NOT EXISTS interest_groups (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES users(id),
    continent_id BIGINT REFERENCES continents(id)
);

-- notification_logs
CREATE TABLE IF NOT EXISTS notification_logs (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      REFERENCES users(id),
    type       VARCHAR(20) NOT NULL,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- land_tax_logs
CREATE TABLE IF NOT EXISTS land_tax_logs (
    id               BIGSERIAL   PRIMARY KEY,
    user_id          BIGINT      REFERENCES users(id),
    territory_count  INTEGER     NOT NULL,
    gp_charged       INTEGER     NOT NULL,
    status           VARCHAR(10) NOT NULL,
    charged_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- items
CREATE TABLE IF NOT EXISTS items (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    item_type   VARCHAR(20)  NOT NULL,
    description VARCHAR(200),
    cost_ap     INTEGER,
    cost_gp     INTEGER,
    daily_limit INTEGER,
    gp_reward   INTEGER,
    icon_url    VARCHAR(100)
);

-- user_items
CREATE TABLE IF NOT EXISTS user_items (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    item_id    BIGINT      NOT NULL REFERENCES items(id),
    quantity   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, item_id)
);

-- item_purchases
CREATE TABLE IF NOT EXISTS item_purchases (
    id                   BIGSERIAL   PRIMARY KEY,
    user_id              BIGINT      REFERENCES users(id),
    item_id              BIGINT      REFERENCES items(id),
    quantity             INTEGER     NOT NULL DEFAULT 1,
    target_territory_id  BIGINT      REFERENCES territories(id),
    purchased_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- season_passes
CREATE TABLE IF NOT EXISTS season_passes (
    id               BIGSERIAL   PRIMARY KEY,
    name             VARCHAR(50) NOT NULL,
    cost_ap          INTEGER     NOT NULL,
    duration_days    INTEGER     NOT NULL DEFAULT 30,
    island_bonus_pct INTEGER     NOT NULL,
    extra_builders   INTEGER     NOT NULL DEFAULT 1,
    tax_exempt_bonus INTEGER     NOT NULL DEFAULT 2,
    build_time_reduction_pct INTEGER NOT NULL DEFAULT 0
);

-- user_season_passes
CREATE TABLE IF NOT EXISTS user_season_passes (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      REFERENCES users(id),
    season_pass_id BIGINT      REFERENCES season_passes(id),
    started_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    is_active      BOOLEAN     NOT NULL DEFAULT true,
    bonus_build_time_reduction_pct INTEGER NOT NULL DEFAULT 0
);

-- island_grades
CREATE TABLE IF NOT EXISTS island_grades (
    id                    BIGSERIAL  PRIMARY KEY,
    name                  VARCHAR(5) NOT NULL UNIQUE,
    grid_size             INTEGER    NOT NULL,
    zone1_radius          INTEGER    NOT NULL,
    zone2_radius          INTEGER    NOT NULL,
    castle_level_required INTEGER    NOT NULL
);

-- home_islands
CREATE TABLE IF NOT EXISTS home_islands (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      UNIQUE NOT NULL REFERENCES users(id),
    level           INTEGER     NOT NULL DEFAULT 1,
    grid_size       INTEGER     NOT NULL DEFAULT 10,
    grade           VARCHAR(5),
    island_grade_id BIGINT      REFERENCES island_grades(id),
    last_harvest_at TIMESTAMP,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- building_types
CREATE TABLE IF NOT EXISTS building_types (
    id                     BIGSERIAL   PRIMARY KEY,
    name                   VARCHAR(30) NOT NULL,
    width                  INTEGER     NOT NULL,
    height                 INTEGER     NOT NULL,
    max_hp                 INTEGER     NOT NULL,
    base_cost_gp           INTEGER     NOT NULL,
    upgrade_cost_gp        INTEGER,
    ap_cost                INTEGER,
    zone_restriction       INTEGER,
    defense_power          INTEGER,
    food_production_rate   INTEGER,
    unit_capacity_per_level INTEGER,
    gp_production_rate     INTEGER,
    build_time_seconds     INTEGER,
    upgrade_time_seconds   INTEGER,
    icon                   VARCHAR(10),
    color_hex             VARCHAR(7),
    display_name           VARCHAR(30),
    category               VARCHAR(20)
);

-- building_level_specs (건물별·레벨별 세부 설정: 레벨별 업그레이드 비용)
CREATE TABLE IF NOT EXISTS building_level_specs (
    id                     BIGSERIAL   PRIMARY KEY,
    building_type_id       BIGINT      NOT NULL REFERENCES building_types(id),
    level                  INTEGER     NOT NULL,
    upgrade_cost_gp        INTEGER,
    max_hp                 INTEGER,
    defense_power          INTEGER,
    food_production_rate   INTEGER,
    unit_capacity_per_level INTEGER,
    gp_production_rate     INTEGER,
    upgrade_time_seconds   INTEGER,
    UNIQUE (building_type_id, level)
);

-- building_castle_limits (건물 종류 × 성 레벨별 최대 개수)
CREATE TABLE IF NOT EXISTS building_castle_limits (
    id               BIGSERIAL PRIMARY KEY,
    building_type_id BIGINT    NOT NULL REFERENCES building_types(id),
    castle_level     INTEGER   NOT NULL,
    max_count        INTEGER   NOT NULL,
    UNIQUE (building_type_id, castle_level)
);

-- building_instances
CREATE TABLE IF NOT EXISTS building_instances (
    id                    BIGSERIAL   PRIMARY KEY,
    territory_id          BIGINT      REFERENCES territories(id),
    island_id             BIGINT      REFERENCES home_islands(id),
    building_type_id      BIGINT      NOT NULL REFERENCES building_types(id),
    user_id               BIGINT      REFERENCES users(id),
    pos_x                 INTEGER     NOT NULL,
    pos_y                 INTEGER     NOT NULL,
    hp                    INTEGER     NOT NULL,
    level                 INTEGER     NOT NULL DEFAULT 1,
    zone                  INTEGER     NOT NULL,
    is_destroyed          BOOLEAN     NOT NULL DEFAULT false,
    stored_gp             INTEGER     NOT NULL DEFAULT 0,
    stored_food           INTEGER     NOT NULL DEFAULT 0,
    workshop_debuff_until TIMESTAMPTZ,
    build_complete_at     TIMESTAMPTZ,
    upgrade_to_level      INTEGER
);

-- global_vaults
CREATE TABLE IF NOT EXISTS global_vaults (
    user_id          BIGINT  PRIMARY KEY REFERENCES users(id),
    stored_gp        INTEGER NOT NULL DEFAULT 0,
    capacity         INTEGER NOT NULL DEFAULT 10000,
    last_transfer_at TIMESTAMPTZ
);

-- unit_types
CREATE TABLE IF NOT EXISTS unit_types (
    id            BIGSERIAL   PRIMARY KEY,
    name          VARCHAR(30) NOT NULL,
    display_name  VARCHAR(30),
    icon          VARCHAR(10),
    color_hex     VARCHAR(7),
    attack_power  INTEGER     NOT NULL,
    defense_power INTEGER     NOT NULL,
    cost_gp       INTEGER     NOT NULL,
    food_cost     INTEGER     NOT NULL,
    level         INTEGER     NOT NULL DEFAULT 1
);

-- unit_type_level_specs (유닛 훈련 레벨별 스펙)
CREATE TABLE IF NOT EXISTS unit_type_level_specs (
    id                      BIGSERIAL PRIMARY KEY,
    unit_type_id            BIGINT    NOT NULL REFERENCES unit_types(id),
    level                   INTEGER   NOT NULL,
    attack_power            INTEGER   NOT NULL,
    defense_power           INTEGER   NOT NULL,
    train_cost_food         INTEGER   NOT NULL,
    required_barracks_level INTEGER   NOT NULL,
    UNIQUE (unit_type_id, level)
);

-- unit_instances
CREATE TABLE IF NOT EXISTS unit_instances (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT    NOT NULL REFERENCES users(id),
    unit_type_id           BIGINT    NOT NULL REFERENCES unit_types(id),
    quantity               INTEGER   NOT NULL,
    -- 유닛이 귀속된 위치. 영토 또는 섬 중 하나만 설정된다 (Stage 2 백필 후 CHECK 추가)
    home_territory_id      BIGINT    REFERENCES territories(id),
    home_island_id         BIGINT    REFERENCES home_islands(id),
    deployed_territory_id  BIGINT    REFERENCES territories(id)
);

-- attack_tokens
CREATE TABLE IF NOT EXISTS attack_tokens (
    user_id         BIGINT  PRIMARY KEY REFERENCES users(id),
    normal_count    INTEGER NOT NULL DEFAULT 0,
    precision_count INTEGER NOT NULL DEFAULT 0
);

-- siege_events
CREATE TABLE IF NOT EXISTS siege_events (
    id                  BIGSERIAL   PRIMARY KEY,
    attacker_id         BIGINT      NOT NULL REFERENCES users(id),
    defender_id         BIGINT      NOT NULL REFERENCES users(id),
    target_territory_id BIGINT      NOT NULL REFERENCES territories(id),
    target_building_id  BIGINT      NOT NULL REFERENCES building_instances(id),
    attack_zone         INTEGER     NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    siege_start_at      TIMESTAMPTZ NOT NULL,
    resolve_at          TIMESTAMPTZ NOT NULL
);

-- siege_results
CREATE TABLE IF NOT EXISTS siege_results (
    id                   BIGSERIAL   PRIMARY KEY,
    siege_id             BIGINT      UNIQUE NOT NULL REFERENCES siege_events(id),
    is_attacker_win      BOOLEAN     NOT NULL,
    attacker_units_lost  INTEGER,
    defender_units_lost  INTEGER,
    looted_gp            INTEGER     NOT NULL DEFAULT 0,
    result_type          VARCHAR(15)
);

-- seasons
CREATE TABLE IF NOT EXISTS seasons (
    id            BIGSERIAL   PRIMARY KEY,
    season_number INTEGER     NOT NULL UNIQUE,
    started_at    TIMESTAMPTZ NOT NULL,
    ended_at      TIMESTAMPTZ,
    processed_at  TIMESTAMPTZ
);

-- FK: auction_histories.season_id → seasons (deferred because seasons is defined later)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_auction_histories_season'
    ) THEN
        ALTER TABLE auction_histories
            ADD CONSTRAINT fk_auction_histories_season
            FOREIGN KEY (season_id) REFERENCES seasons(id);
    END IF;
END $$;

-- season_territory_holds
CREATE TABLE IF NOT EXISTS season_territory_holds (
    id           BIGSERIAL   PRIMARY KEY,
    season_id    BIGINT      NOT NULL REFERENCES seasons(id),
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    territory_id BIGINT      NOT NULL REFERENCES territories(id),
    grade        VARCHAR(1)  NOT NULL,
    held_from    TIMESTAMP   NOT NULL,
    held_until   TIMESTAMP
);

-- season_pass_progress
CREATE TABLE IF NOT EXISTS season_pass_progress (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT    NOT NULL REFERENCES users(id),
    season_id BIGINT    NOT NULL REFERENCES seasons(id),
    level     INTEGER   NOT NULL DEFAULT 1,
    xp        INTEGER   NOT NULL DEFAULT 0,
    UNIQUE (user_id, season_id)
);

-- season_pass_level_rewards
CREATE TABLE IF NOT EXISTS season_pass_level_rewards (
    id          BIGSERIAL    PRIMARY KEY,
    season_id   BIGINT       NOT NULL REFERENCES seasons(id),
    level       INTEGER      NOT NULL,
    reward_name VARCHAR(100) NOT NULL
);

-- season_pass_reward_claims
CREATE TABLE IF NOT EXISTS season_pass_reward_claims (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    reward_id  BIGINT      NOT NULL REFERENCES season_pass_level_rewards(id),
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- user_trophies
CREATE TABLE IF NOT EXISTS user_trophies (
    user_id               BIGINT      PRIMARY KEY REFERENCES users(id),
    score                 INTEGER     NOT NULL DEFAULT 0,
    league                VARCHAR(10) NOT NULL DEFAULT 'BRONZE',
    season_id             BIGINT      NOT NULL REFERENCES seasons(id),
    last_reset_season_id  BIGINT      REFERENCES seasons(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- trophy_logs
CREATE TABLE IF NOT EXISTS trophy_logs (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    season_id   BIGINT      NOT NULL REFERENCES seasons(id),
    siege_id    BIGINT      REFERENCES siege_events(id),
    delta       INTEGER     NOT NULL,
    reason      VARCHAR(30) NOT NULL,
    score_after INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- season_rewards
CREATE TABLE IF NOT EXISTS season_rewards (
    id                     BIGSERIAL   PRIMARY KEY,
    user_id                BIGINT      NOT NULL REFERENCES users(id),
    season_id              BIGINT      NOT NULL REFERENCES seasons(id),
    league                 VARCHAR(10) NOT NULL,
    gp_reward              INTEGER     NOT NULL,
    attack_token_normal    INTEGER     NOT NULL DEFAULT 0,
    attack_token_precision INTEGER     NOT NULL DEFAULT 0,
    title_reward           VARCHAR(30),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- guilds
CREATE TABLE IF NOT EXISTS guilds (
    id                BIGSERIAL    PRIMARY KEY,
    name              VARCHAR(20)  NOT NULL UNIQUE,
    description       VARCHAR(200),
    emblem            VARCHAR(255),
    master_id         BIGINT       NOT NULL REFERENCES users(id),
    max_members       INTEGER      NOT NULL DEFAULT 30,
    recruiting_status VARCHAR(6)   NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- guild_members
CREATE TABLE IF NOT EXISTS guild_members (
    id        BIGSERIAL    PRIMARY KEY,
    guild_id  BIGINT       NOT NULL REFERENCES guilds(id),
    user_id   BIGINT       NOT NULL REFERENCES users(id),
    role      VARCHAR(10)  NOT NULL DEFAULT 'MEMBER',
    status    VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    message   VARCHAR(200),
    joined_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
