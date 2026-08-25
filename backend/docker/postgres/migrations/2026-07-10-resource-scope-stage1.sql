-- 자원 스코프 전환 Stage 1 — 비파괴 스키마 추가
-- 설계: report/design/2026-07-10-all-resource-scope.md
--
-- 이 마이그레이션은 컬럼만 추가한다. 기존 코드는 이 컬럼을 읽지도 쓰지도 않으므로
-- 적용 후에도 모든 API 가 그대로 동작한다.
--
-- 1) unit_instances.home_territory_id / home_island_id — 유닛 귀속 위치.
--    nullable 이라 ddl-auto 도 추가하지만, FK 를 명시하려고 여기서 만든다.
--    CHECK(둘 중 하나는 NOT NULL) 제약은 Stage 2 백필 완료 후 추가한다.
-- 2) building_instances.stored_food — 성·저장소가 보관하는 식량.
--    NOT NULL 이라 ddl-auto=update 가 기존 행이 있는 테이블에 추가하지 못한다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-resource-scope-stage1.sql

BEGIN;

ALTER TABLE unit_instances
    ADD COLUMN IF NOT EXISTS home_territory_id BIGINT REFERENCES territories(id);

ALTER TABLE unit_instances
    ADD COLUMN IF NOT EXISTS home_island_id BIGINT REFERENCES home_islands(id);

ALTER TABLE building_instances
    ADD COLUMN IF NOT EXISTS stored_food INTEGER NOT NULL DEFAULT 0;

COMMIT;
