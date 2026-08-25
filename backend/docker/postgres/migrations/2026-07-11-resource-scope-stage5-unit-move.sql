-- Stage 5+7: 유닛 위치 간 이동. 이동 중 유닛은 도착 예정 시각을 갖는다(도착 전까지 방어·배치 불가).
-- NULL 이면 이동 중이 아님. 기존 유닛은 전부 이동 중이 아니므로 NULL 로 둔다.
ALTER TABLE unit_instances ADD COLUMN IF NOT EXISTS move_complete_at TIMESTAMP;
