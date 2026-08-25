-- 기존 DB 마이그레이션 (신규 환경은 init.sql + 시더로 충분)
--
-- 1) user_season_passes.bonus_build_time_reduction_pct — NOT NULL 컬럼은 ddl-auto=update 가
--    기존 행이 있는 테이블에 추가하지 못하므로 기본값과 함께 수동으로 추가한다.
-- 2) season_pass_level_rewards.reward_kind 길이 확장 (VARCHAR(10) → VARCHAR(30))
-- 3) SeasonPassLevelRewardSeeder 는 시즌당 한 번만 동작하므로, 이미 보상이 시드된
--    활성 시즌에는 새 보상(15레벨 건설 시간 감소)이 들어가지 않는다. 여기서 채운다.
--
-- 실행:
--   docker compose exec -T postgres psql -U postgres -d territorial_auction \
--     < backend/docker/postgres/migrations/2026-07-10-season-pass-build-time-reward.sql

BEGIN;

ALTER TABLE user_season_passes
    ADD COLUMN IF NOT EXISTS bonus_build_time_reduction_pct INTEGER NOT NULL DEFAULT 0;

-- BUILD_TIME_REDUCTION 은 20자라 기존 VARCHAR(10) 에 들어가지 않는다.
ALTER TABLE season_pass_level_rewards ALTER COLUMN reward_kind TYPE VARCHAR(30);

INSERT INTO season_pass_level_rewards (season_id, level, track, reward_name, reward_kind, quantity)
SELECT s.id, 15, 'PREMIUM', '건설 시간 10% 감소', 'BUILD_TIME_REDUCTION', 10
FROM seasons s
WHERE s.started_at <= now() AND (s.ended_at IS NULL OR s.ended_at >= now())
  AND NOT EXISTS (
      SELECT 1 FROM season_pass_level_rewards r
      WHERE r.season_id = s.id AND r.reward_kind = 'BUILD_TIME_REDUCTION'
  );

COMMIT;
