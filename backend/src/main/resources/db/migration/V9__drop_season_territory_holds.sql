-- ranking-service 추출(#36) 이후 모놀리식 고아 테이블 정리.
-- 영토 점유 기록(season_territory_holds)은 ranking-service DB(ranking)가 소유하므로
-- 모놀리식은 더 이상 매핑하지 않는다. users·territories로의 아웃바운드 FK만 있고
-- 이 테이블을 참조하는 쪽은 없다. CASCADE는 잔존 제약 방어용.
DROP TABLE IF EXISTS public.season_territory_holds CASCADE;
