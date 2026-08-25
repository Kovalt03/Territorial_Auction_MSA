-- 공성전 Stage 4: 유닛 건물 주둔. 방어 배치 유닛이 주둔한 건물을 기록한다.
-- 공성 판정 시 공격받는 Zone의 건물에 주둔한 병력만 방어에 참여한다.
-- 기존 배치 유닛(deployed_territory_id 있음)은 building NULL — 다음 배치부터 건물 지정.

ALTER TABLE unit_instances
    ADD COLUMN IF NOT EXISTS deployed_building_id BIGINT REFERENCES building_instances(id);
