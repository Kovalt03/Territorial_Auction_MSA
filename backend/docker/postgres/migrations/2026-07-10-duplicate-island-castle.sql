-- 기존 DB 정리 (신규 환경은 해당 없음)
--
-- 섬에는 성이 하나만 존재해야 하지만 이를 막는 검증이 없어, Zone1에 여유가 있으면
-- 두 번째 성을 지을 수 있었다. 검증(CASTLE_ALREADY_EXISTS)은 추가했으나 이미 생성된
-- 중복 성은 남아 있으므로 제거한다.
--
-- 섬마다 가장 오래된 성(최소 id = 섬 생성 시 배치된 시작 성) 하나만 남긴다.
-- 시작 성은 섬 등급(castleLevelRequired)의 기준이 되므로 반드시 보존해야 한다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-duplicate-island-castle.sql

BEGIN;

DELETE FROM building_instances bi
USING (
    SELECT b.island_id, MIN(b.id) AS keep_id
    FROM building_instances b
    JOIN building_types bt ON bt.id = b.building_type_id AND bt.name = 'CASTLE'
    WHERE b.island_id IS NOT NULL
    GROUP BY b.island_id
    HAVING COUNT(*) > 1
) dup
JOIN building_types bt2 ON bt2.name = 'CASTLE'
WHERE bi.island_id = dup.island_id
  AND bi.building_type_id = bt2.id
  AND bi.id <> dup.keep_id;

COMMIT;
