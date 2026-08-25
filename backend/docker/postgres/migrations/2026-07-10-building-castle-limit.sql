-- 기존 DB 마이그레이션 (신규 환경은 init.sql 로 충분)
--
-- 성 레벨별 최대 건물 수는 "섬 전체 총량 1개 값"이 아니라 "건물 종류마다 성 레벨별 상한"으로
-- 재설계됐다. 이전 max_buildings 컬럼은 강제된 적이 없고 값도 전부 NULL 이므로 그냥 제거한다.
-- building_castle_limits 테이블은 ddl-auto=update 가 만들어 준다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-building-castle-limit.sql

BEGIN;

ALTER TABLE building_types       DROP COLUMN IF EXISTS max_buildings;
ALTER TABLE building_level_specs DROP COLUMN IF EXISTS max_buildings;

COMMIT;
