-- 시즌 보상은 (시즌, 유저)당 정확히 1행. 이 레코드가 "이 유저 지급 완료"의 durable 마커가 되어,
-- 유저별 독립 트랜잭션 배치가 실패·재실행돼도 완료된 유저를 건너뛰고 전진 복구할 수 있게 한다.
-- combat 지급(creditGp/creditAttackTokens)은 이미 멱등(commandKey)이라, 이력만 UNIQUE로 막으면 재실행이 무해해진다.

-- 기존 중복 정리: (season_id, user_id)별 가장 낮은 id만 남김.
DELETE FROM season_rewards a
USING season_rewards b
WHERE a.season_id = b.season_id
  AND a.user_id = b.user_id
  AND a.id > b.id;

ALTER TABLE season_rewards
    ADD CONSTRAINT uq_season_rewards_season_user UNIQUE (season_id, user_id);
