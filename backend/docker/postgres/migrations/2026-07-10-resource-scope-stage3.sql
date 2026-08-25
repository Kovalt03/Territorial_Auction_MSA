-- 자원 스코프 전환 Stage 3 — 금고 이전을 저장 공간에 연동
-- 설계: report/design/2026-07-10-all-resource-scope.md
--
-- GlobalVaultService.transfer 가 지갑 대신 위치 저장 공간(성 + 저장소)을 읽고 쓰도록
-- 바뀐다. 코드 변경만으로 충분하지만, 금고 기본 용량이 500 → 10,000 으로 상향되므로
-- 아직 500(옛 기본값) 그대로인 기존 금고만 올린다. 관리자가 개별 조정한 값은 건드리지 않는다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-resource-scope-stage3.sql

BEGIN;

UPDATE global_vaults SET capacity = 10000 WHERE capacity = 500;

COMMIT;
