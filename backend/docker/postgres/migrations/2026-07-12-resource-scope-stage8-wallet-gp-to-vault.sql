-- Stage 8: 토지세가 지갑이 아니라 금고에서 차감되도록 바뀌면서, 기존 지갑 GP 를 금고로 이전한다.
-- 용량 초과분도 무시하고 전액 이전한다(초과 상태는 유저가 쓰면서 자연히 해소되며, 이후 입금만 차단).
-- available_gp 컬럼 자체는 Stage 9 에서 DROP.

-- 1) 금고가 이미 있는 유저: 지갑 GP 를 금고에 더한다.
UPDATE global_vaults gv
SET stored_gp = gv.stored_gp + w.available_gp
FROM wallets w
WHERE w.user_id = gv.user_id AND w.available_gp > 0;

-- 2) 금고가 없는 유저(방어적): 지갑 GP 로 금고를 생성한다.
INSERT INTO global_vaults (user_id, stored_gp, capacity)
SELECT w.user_id, w.available_gp, 10000
FROM wallets w
WHERE w.available_gp > 0
  AND NOT EXISTS (SELECT 1 FROM global_vaults gv WHERE gv.user_id = w.user_id);

-- 3) 지갑 GP 비우기.
UPDATE wallets SET available_gp = 0 WHERE available_gp > 0;
