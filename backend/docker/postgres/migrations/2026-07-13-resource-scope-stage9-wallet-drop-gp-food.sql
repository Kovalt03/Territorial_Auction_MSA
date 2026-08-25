-- Stage 9: 지갑에서 GP·식량을 완전히 제거한다.
-- GP 는 Stage 8 에서 금고로 이전 완료 → available_gp DROP.
-- 식량은 위치(영토/섬 저장소)로 일원화되어 지갑 식량은 방치(이전하지 않음) → available_food DROP.
-- 이후 GP 표시=금고+저장소 합, 식량 표시=위치 저장소 합.

ALTER TABLE wallets DROP COLUMN IF EXISTS available_gp;
ALTER TABLE wallets DROP COLUMN IF EXISTS available_food;
