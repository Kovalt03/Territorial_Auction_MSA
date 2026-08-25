-- 기존 DB 마이그레이션 (신규 환경은 init.sql 로 충분)
--
-- init.sql 은 unit_types.food_cost_per_hour (NOT NULL) 로 선언했는데 엔티티는 foodCost →
-- food_cost 로 매핑한다. 그래서 두 컬럼이 함께 존재하고, food_cost_per_hour 가 NOT NULL 이라
-- JPA INSERT 가 실패했다. 실제로 unit_types 테이블은 비어 있었고 유닛 생산이 불가능했다.
--
-- unit_type_level_specs 와 unit_types 의 표시용 컬럼은 nullable 이거나 기본값이 있어
-- ddl-auto=update 가 알아서 추가한다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-unit-types.sql

BEGIN;

ALTER TABLE unit_types DROP COLUMN IF EXISTS food_cost_per_hour;

COMMIT;
