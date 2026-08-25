-- 자원 스코프 전환 Stage 2 — 유닛 귀속지 백필 + CHECK 제약
-- 설계: report/design/2026-07-10-all-resource-scope.md
--
-- 이 단계는 유닛의 귀속 위치만 채운다. 지갑 잔고(available_gp / available_food)는
-- 건드리지 않는다 — Stage 4 에서 코드가 새 출처를 읽기 시작할 때 함께 옮긴다.
-- 여기서 지갑을 비우면 Stage 3~4 사이에 BuildingService 가 여전히 지갑을 읽어
-- 건설이 전부 막힌다.
--
-- 귀속 규칙:
--   1. 방어 배치 중인 유닛(deployed_territory_id IS NOT NULL) → 그 영토 귀속.
--      유저가 놓아둔 자리를 존중한다. 해당 영토의 유닛 슬롯을 초과할 수 있으나 허용한다.
--   2. 나머지 → 유저의 홈 아일랜드 귀속. 어느 영토에서 생산됐는지 이력이 없으므로
--      섬이 가장 중립적인 초기 귀속지다.
--   3. 섬이 없는 유저의 유닛은 남는다(이론상 없음). CHECK 제약 전에 확인한다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-resource-scope-stage2.sql

BEGIN;

-- 1. 배치 중인 유닛 → 배치된 영토 귀속
UPDATE unit_instances
SET home_territory_id = deployed_territory_id
WHERE deployed_territory_id IS NOT NULL
  AND home_territory_id IS NULL
  AND home_island_id IS NULL;

-- 2. 나머지 → 홈 아일랜드 귀속
UPDATE unit_instances u
SET home_island_id = h.id
FROM home_islands h
WHERE h.user_id = u.user_id
  AND u.home_territory_id IS NULL
  AND u.home_island_id IS NULL;

-- 3. 귀속지가 없는 유닛이 남으면 CHECK 제약이 실패한다. 미리 확인.
DO $$
DECLARE orphan_count INTEGER;
BEGIN
    SELECT count(*) INTO orphan_count
    FROM unit_instances
    WHERE home_territory_id IS NULL AND home_island_id IS NULL;

    IF orphan_count > 0 THEN
        RAISE EXCEPTION '귀속지 없는 유닛 % 건. 홈 아일랜드가 없는 유저를 확인하라.', orphan_count;
    END IF;
END $$;

-- 4. 영토와 섬은 배타적이다.
ALTER TABLE unit_instances
    ADD CONSTRAINT unit_instances_home_location_check
    CHECK (
        (home_territory_id IS NOT NULL AND home_island_id IS NULL)
        OR (home_territory_id IS NULL AND home_island_id IS NOT NULL)
    );

COMMIT;
