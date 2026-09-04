-- map-service 추출(#38) 이후 모놀리식 고아 테이블 정리.
-- 영토·대륙·등급·토지세·수입·보너스타일·색상이력·경매상태 프로젝션은 map-service DB(map)가 소유하므로
-- 모놀리식은 더 이상 매핑하지 않는다(domain/map 삭제 완료).
--
-- CASCADE로 이 테이블들을 참조하던 잔존 FK 제약을 함께 제거한다.
-- 특히 wishlists.territory_id → territories FK가 제거된다(Wishlist 엔티티는 이미 territoryId(Long)로 전환).
-- 참조하던 테이블(wishlists 등) 자체는 보존되고 FK 제약만 사라진다.
DROP TABLE IF EXISTS public.bonus_tiles CASCADE;
DROP TABLE IF EXISTS public.color_histories CASCADE;
DROP TABLE IF EXISTS public.land_tax_logs CASCADE;
DROP TABLE IF EXISTS public.territory_production_logs CASCADE;
DROP TABLE IF EXISTS public.territory_auction_status CASCADE;
DROP TABLE IF EXISTS public.territories CASCADE;
DROP TABLE IF EXISTS public.territory_grades CASCADE;
DROP TABLE IF EXISTS public.continents CASCADE;
