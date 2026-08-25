-- 공성 스키마 정합 (검증 중 발견):
-- A) siege_events.target_building_id: 정밀 공격이 아닌 일반 공성은 대상 건물이 없어 NULL이어야 한다.
--    과거 스키마의 NOT NULL 제약이 남아 일반 공성 선언이 전부 실패하던 것을 완화한다.
--    (엔티티는 이미 nullable — ddl-auto=update는 제약을 완화하지 못해 기존 DB만 영향.)
ALTER TABLE siege_events ALTER COLUMN target_building_id DROP NOT NULL;

-- C) siege_structures 좌표 컬럼을 프로젝트 snake_case 컨벤션(coord_x/coord_y)으로 통일한다.
--    엔티티에 @Column(name=...)이 없어 coordx/coordy로 생성됐던 것을 정정. 기존 컬럼이 있으면 rename.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'siege_structures' AND column_name = 'coordx') THEN
        ALTER TABLE siege_structures RENAME COLUMN coordx TO coord_x;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'siege_structures' AND column_name = 'coordy') THEN
        ALTER TABLE siege_structures RENAME COLUMN coordy TO coord_y;
    END IF;
END $$;
