-- 기존 DB 마이그레이션 (신규 환경은 init.sql 로 충분)
--
-- 1) island_grades: 엔티티 필드 zone1Radius 를 Hibernate 기본 전략이 zone1radius 로 매핑해
--    init.sql 의 zone1_radius 와 어긋나 있었다. 컬럼명을 명시로 고정했으므로 기존 컬럼을 rename.
-- 2) territory_grades: Zone 반경을 등급 설정값으로 승격 — NOT NULL 컬럼은 ddl-auto=update 가
--    기존 행이 있는 테이블에 추가하지 못하므로 수동으로 채운 뒤 제약을 건다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     -f /dev/stdin < backend/docker/postgres/migrations/2026-07-09-zone-radius.sql

BEGIN;

ALTER TABLE island_grades RENAME COLUMN zone1radius TO zone1_radius;
ALTER TABLE island_grades RENAME COLUMN zone2radius TO zone2_radius;

ALTER TABLE territory_grades ADD COLUMN IF NOT EXISTS zone1_radius INTEGER;
ALTER TABLE territory_grades ADD COLUMN IF NOT EXISTS zone2_radius INTEGER;

-- 값은 부팅 시 TerritoryGradeSeeder 가 yml 로 덮어쓴다. NOT NULL 제약을 걸기 위한 임시 채움.
UPDATE territory_grades SET zone1_radius = GREATEST(grid_size / 5, 1) WHERE zone1_radius IS NULL;
UPDATE territory_grades SET zone2_radius = GREATEST(grid_size * 2 / 5, 2) WHERE zone2_radius IS NULL;

ALTER TABLE territory_grades ALTER COLUMN zone1_radius SET NOT NULL;
ALTER TABLE territory_grades ALTER COLUMN zone2_radius SET NOT NULL;

COMMIT;
