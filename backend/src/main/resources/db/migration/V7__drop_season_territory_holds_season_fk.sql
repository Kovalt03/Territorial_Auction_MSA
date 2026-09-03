-- season-service 추출: season_territory_holds는 ranking(모놀리식)이 계속 소유하지만,
-- season_id는 이제 season-service DB가 소유하는 시즌 식별자다. 모놀리식 seasons 테이블로의
-- 외래키를 유지하면 season-service가 발급한 시즌 id로 점유 기록을 삽입할 때 FK 위반이 난다.
-- 컬럼(season_id)은 Long 식별자로 유지하고 FK 제약만 제거한다.
-- 고아가 된 season 도메인 테이블(seasons·season_passes·user_trophies 등) 자체 정리는 별도 마이그레이션에서 수행한다.
ALTER TABLE public.season_territory_holds
    DROP CONSTRAINT IF EXISTS fkewy9ccwq8pew7xyx8wyl7nivg;
