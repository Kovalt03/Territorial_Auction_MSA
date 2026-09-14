-- 낙관적 락 컬럼. 입찰 분산락(lock:auction:{id})이 lease(10s) 만료로 뚫려 두 스레드가
-- 같은 경매에 동시 입찰해도, 커밋은 하나만 성공(version++)하고 진 쪽은 OptimisticLock 실패로
-- 롤백된다 → 이미 등록된 escrow 보상이 진 쪽 AP 잠금을 되돌린다. 정확성을 락 타이밍에서 분리.

ALTER TABLE auctions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
