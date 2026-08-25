-- 공성전 Stage 1 (기반): 보호/점유 분리 + 유닛 buildingDamage + 신규 유닛
-- 신규 유닛(CAVALRY·SIEGE_ENGINE·GUARD·CATAPULT·SCOUT)은 UnitTypeSeeder가 unit-types.yml에서 시드.

-- 1) 영토 공성 보호 만료 시각(점유와 별개). 기존 점유 영토는 NULL = 보호 없음(즉시 공성 가능).
ALTER TABLE territories ADD COLUMN IF NOT EXISTS protected_until TIMESTAMP;

-- 2) 유닛 건물 피해 스탯. 기존 유닛은 0 → 아래에서 초기값 백필.
ALTER TABLE unit_types ADD COLUMN IF NOT EXISTS building_damage INTEGER NOT NULL DEFAULT 0;

UPDATE unit_types SET building_damage = 2 WHERE name = 'INFANTRY';
UPDATE unit_types SET building_damage = 1 WHERE name = 'ARCHER';
UPDATE unit_types SET building_damage = 6 WHERE name = 'KNIGHT';
