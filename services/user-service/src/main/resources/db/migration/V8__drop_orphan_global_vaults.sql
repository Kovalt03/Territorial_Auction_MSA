-- 고아 테이블 정리: GP 금고는 combat-service(GlobalVault)로 이관됐다.
-- user DB의 global_vaults는 자원 스코프 전환 이후 사용처가 없어 제거한다(엔티티·쿼리 참조 0).
DROP TABLE IF EXISTS global_vaults;
