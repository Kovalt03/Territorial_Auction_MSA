-- 낙찰 이력은 경매당 정확히 1행이어야 한다. 정산이 재시도되거나(원격 커밋 후 로컬 롤백),
-- 다중 인스턴스에서 같은 경매를 동시에 정산할 때 중복 이력이 쌓이는 것을 DB 레벨에서 차단한다.
-- (winner AP 소비·성 생성은 이미 멱등이므로, 이력만 막으면 재정산이 무해해진다.)

-- 기존에 쌓였을 수 있는 중복 이력을 정리: auction_id별 가장 낮은 id만 남기고 제거.
DELETE FROM auction_histories a
USING auction_histories b
WHERE a.auction_id = b.auction_id
  AND a.id > b.id;

ALTER TABLE auction_histories
    ADD CONSTRAINT uq_auction_histories_auction_id UNIQUE (auction_id);
