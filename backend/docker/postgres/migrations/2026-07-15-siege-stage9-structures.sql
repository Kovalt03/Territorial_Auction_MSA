-- 공성전 Stage 9: 공성 건물. 공성 선언 시 대상 영토 인접 타일에 짓는 임시 구조물(주둔지/공성타워/보급소).
-- 해당 공성에 종속되며 판정 후 전부 삭제된다. 주둔지=공격 병력 상한, 공성타워=공격력 버프, 보급소=쿨다운 완화.

CREATE TABLE IF NOT EXISTS siege_structures (
    id       BIGSERIAL PRIMARY KEY,
    siege_id BIGINT  NOT NULL REFERENCES siege_events(id),
    type     VARCHAR(10) NOT NULL,
    coord_x  INTEGER NOT NULL,
    coord_y  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_siege_structures_siege_id ON siege_structures(siege_id);

-- 보급소로 완화된 실패 후 공격 쿨다운(시간). NULL이면 기본 쿨다운 사용(구 데이터).
ALTER TABLE siege_results
    ADD COLUMN IF NOT EXISTS applied_cooldown_hours INTEGER;
