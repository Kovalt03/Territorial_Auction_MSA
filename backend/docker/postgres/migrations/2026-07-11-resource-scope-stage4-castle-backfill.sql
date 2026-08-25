-- Stage 4: 성이 기본 저장 기능을 갖게 되면서, 점유 영토에는 항상 성이 있어야 첫 건물을 지을 수 있다.
-- 낙찰 경로(AuctionLifecycleService)는 앞으로 성을 자동 생성하지만, 이미 점유 중이면서
-- 성이 없는 기존 영토는 여기서 backfill 한다. 초기 stored_gp 는 0 — 영토 수입·성 생산으로 채운다.
INSERT INTO building_instances (territory_id, building_type_id, pos_x, pos_y, hp, zone, level, stored_gp, stored_food, is_destroyed)
SELECT t.id,
       bt.id,
       (tg.grid_size / 2) - 1,
       (tg.grid_size / 2) - 1,
       bt.max_hp,
       1,
       1,
       0,
       0,
       false
FROM territories t
JOIN territory_grades tg ON tg.id = t.grade_id
CROSS JOIN building_types bt
WHERE bt.name = 'CASTLE'
  AND t.status = 'OCCUPIED'
  AND NOT EXISTS (
      SELECT 1
      FROM building_instances bi
      JOIN building_types b2 ON b2.id = bi.building_type_id
      WHERE bi.territory_id = t.id AND b2.name = 'CASTLE');
