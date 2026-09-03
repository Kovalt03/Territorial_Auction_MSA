-- season-service 추출(#34) 이후 모놀리식 고아 테이블 정리.
-- 시즌·시즌패스·미션·트로피 데이터는 season-service DB(season)가 소유하므로 모놀리식은 더 이상 매핑하지 않는다.
-- season_territory_holds(ranking 소유)는 유지 — season_id만 Long으로 보관(FK는 V7에서 이미 제거).
-- 자식(FK 보유) → 부모 순. CASCADE는 잔존 FK 방어용.
DROP TABLE IF EXISTS public.season_pass_reward_claims CASCADE;
DROP TABLE IF EXISTS public.season_pass_progress CASCADE;
DROP TABLE IF EXISTS public.user_mission_progress CASCADE;
DROP TABLE IF EXISTS public.season_pass_level_rewards CASCADE;
DROP TABLE IF EXISTS public.season_missions CASCADE;
DROP TABLE IF EXISTS public.season_rewards CASCADE;
DROP TABLE IF EXISTS public.user_season_passes CASCADE;
DROP TABLE IF EXISTS public.trophy_logs CASCADE;
DROP TABLE IF EXISTS public.user_trophies CASCADE;
DROP TABLE IF EXISTS public.season_passes CASCADE;
DROP TABLE IF EXISTS public.seasons CASCADE;
