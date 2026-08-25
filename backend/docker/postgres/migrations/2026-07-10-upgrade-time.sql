-- 기존 DB 마이그레이션 (신규 환경은 init.sql 로 충분)
--
-- 1) season_passes.build_time_reduction_pct — NOT NULL 컬럼은 ddl-auto=update 가
--    기존 행이 있는 테이블에 추가하지 못하므로 기본값과 함께 수동으로 추가한다.
--    값 자체는 부팅 시 SeasonPassSeeder 가 yml 로 덮어쓴다.
-- 2) building_types.upgrade_time_seconds / building_level_specs.upgrade_time_seconds /
--    building_instances.upgrade_to_level 은 nullable 이라 ddl-auto 가 알아서 추가한다.
-- 3) 섬 B등급 gridSize 15 → 16 (짝수 통일). 이미 B등급인 섬은 그리드가 1칸 넓어지는데,
--    건물 좌표는 그대로 유효하므로 별도 이전 작업이 필요 없다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-upgrade-time.sql

BEGIN;

ALTER TABLE season_passes
    ADD COLUMN IF NOT EXISTS build_time_reduction_pct INTEGER NOT NULL DEFAULT 0;

COMMIT;
