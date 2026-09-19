-- 보상 수령 중복 방지: claimReward의 existsBy 검사는 락 없는 check-then-insert라
-- 동시 수령이 함께 통과해 ITEM 보상이 이중 지급될 수 있었다(grantByType는 멱등키 없음).
-- 완료 마커에 UNIQUE를 걸어, saveAndFlush가 원격 지급 전에 실패하도록 한다.

-- 기존 레이스로 생겼을 수 있는 중복 행을 정리(각 user_id·reward_id의 최소 id만 남김).
DELETE FROM season_pass_reward_claims c
    USING season_pass_reward_claims dup
WHERE c.user_id = dup.user_id
  AND c.reward_id = dup.reward_id
  AND c.id > dup.id;

ALTER TABLE season_pass_reward_claims
    ADD CONSTRAINT uq_reward_claim_user_reward UNIQUE (user_id, reward_id);
