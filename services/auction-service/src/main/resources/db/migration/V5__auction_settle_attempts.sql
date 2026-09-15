-- 정산 재시도 횟수. 정산이 실패할 때마다 증가하며, 상한(AuctionPolicy.MAX_SETTLE_ATTEMPTS)에
-- 도달하면 미정산 재시도 루프에서 제외한다(settled=false AND settle_attempts>=상한 = "정산 실패", 수동 확인 대상).
-- 원격 호출(occupy·consume·castle)이 전부 멱등이라 일시 실패는 재시도로 자가치유되고, 영구 실패만 격리된다.

ALTER TABLE auctions
    ADD COLUMN settle_attempts INTEGER NOT NULL DEFAULT 0;
