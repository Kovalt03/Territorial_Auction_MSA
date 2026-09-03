-- season-service 초기 스키마. user_id·siege_id는 외부 서비스 소유 식별자(FK 없음).
-- season 내부 FK(season_id·mission_id·reward_id·season_pass_id)만 유지.

CREATE TABLE seasons (
    id            BIGSERIAL PRIMARY KEY,
    season_number INTEGER   NOT NULL,
    started_at    TIMESTAMP NOT NULL,
    ended_at      TIMESTAMP,
    processed_at  TIMESTAMP
);

CREATE TABLE season_passes (
    id                       BIGSERIAL PRIMARY KEY,
    name                     VARCHAR(50) NOT NULL,
    cost_ap                  INTEGER     NOT NULL,
    duration_days            INTEGER     NOT NULL DEFAULT 30,
    island_bonus_pct         INTEGER     NOT NULL,
    extra_builders           INTEGER     NOT NULL DEFAULT 1,
    tax_exempt_bonus         INTEGER     NOT NULL DEFAULT 2,
    build_time_reduction_pct INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE user_season_passes (
    id                             BIGSERIAL PRIMARY KEY,
    user_id                        BIGINT,
    season_pass_id                 BIGINT REFERENCES season_passes (id),
    started_at                     TIMESTAMP NOT NULL,
    expires_at                     TIMESTAMP NOT NULL,
    is_active                      BOOLEAN   NOT NULL DEFAULT TRUE,
    bonus_build_time_reduction_pct INTEGER   NOT NULL DEFAULT 0
);
CREATE INDEX idx_user_season_passes_user ON user_season_passes (user_id);

CREATE TABLE season_missions (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    goal_count   INTEGER      NOT NULL,
    period       VARCHAR(10)  NOT NULL,
    sort_order   INTEGER      NOT NULL,
    title        VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(20)  NOT NULL,
    xp_reward    INTEGER      NOT NULL,
    season_id    BIGINT       NOT NULL REFERENCES seasons (id),
    CONSTRAINT season_missions_period_check CHECK (period IN ('DAILY', 'WEEKLY', 'SEASON'))
);

CREATE TABLE user_mission_progress (
    id                BIGSERIAL PRIMARY KEY,
    claimed           BOOLEAN   NOT NULL,
    completed_count   INTEGER   NOT NULL,
    period_started_at TIMESTAMP NOT NULL,
    mission_id        BIGINT    NOT NULL REFERENCES season_missions (id),
    user_id           BIGINT    NOT NULL,
    CONSTRAINT uq_user_mission UNIQUE (user_id, mission_id)
);

CREATE TABLE season_pass_progress (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT  NOT NULL,
    season_id BIGINT  NOT NULL REFERENCES seasons (id),
    level     INTEGER NOT NULL DEFAULT 1,
    xp        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_pass_progress UNIQUE (user_id, season_id)
);

CREATE TABLE season_pass_level_rewards (
    id          BIGSERIAL PRIMARY KEY,
    season_id   BIGINT       NOT NULL REFERENCES seasons (id),
    level       INTEGER      NOT NULL,
    reward_name VARCHAR(100) NOT NULL,
    track       VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    reward_kind VARCHAR(30)  NOT NULL DEFAULT 'ITEM',
    item_type   VARCHAR(20),
    quantity    INTEGER      NOT NULL DEFAULT 1
);

CREATE TABLE season_pass_reward_claims (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    reward_id  BIGINT    NOT NULL REFERENCES season_pass_level_rewards (id),
    claimed_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE season_rewards (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL,
    season_id              BIGINT      NOT NULL REFERENCES seasons (id),
    league                 VARCHAR(10) NOT NULL,
    gp_reward              INTEGER     NOT NULL,
    attack_token_normal    INTEGER     NOT NULL DEFAULT 0,
    attack_token_precision INTEGER     NOT NULL DEFAULT 0,
    title_reward           VARCHAR(30),
    created_at             TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE user_trophies (
    user_id              BIGINT      PRIMARY KEY,
    score                INTEGER     NOT NULL DEFAULT 0,
    league               VARCHAR(10) NOT NULL DEFAULT 'BRONZE',
    season_id            BIGINT      NOT NULL REFERENCES seasons (id),
    last_reset_season_id BIGINT,
    updated_at           TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_trophies_score ON user_trophies (score DESC);

CREATE TABLE trophy_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    season_id   BIGINT      NOT NULL REFERENCES seasons (id),
    siege_id    BIGINT,
    delta       INTEGER     NOT NULL,
    reason      VARCHAR(30) NOT NULL,
    score_after INTEGER     NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
