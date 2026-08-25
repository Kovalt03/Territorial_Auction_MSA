-- 공성전 Stage 3: 공격 병력 커밋. 선언 시 공격자가 커밋한 병력(유닛 타입별 수량)을 저장한다.
-- 판정(30분 뒤) 시점에 이 기록으로 전투를 계산하고, 생존분은 공격자에게 환원 후 행을 삭제한다.

CREATE TABLE IF NOT EXISTS siege_forces (
    id           BIGSERIAL PRIMARY KEY,
    siege_id     BIGINT  NOT NULL REFERENCES siege_events(id),
    unit_type_id BIGINT  NOT NULL REFERENCES unit_types(id),
    quantity     INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_siege_forces_siege_id ON siege_forces(siege_id);
