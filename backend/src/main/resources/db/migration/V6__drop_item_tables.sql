-- item-service 추출(#32) 이후 모놀리식 고아 테이블 정리.
-- 아이템 상점·인벤토리·구매이력은 item-service DB가 소유하므로 모놀리식은 더 이상 매핑하지 않는다.
-- 자식(FK 보유) → 부모 순. users·territories는 유지(item_purchases/user_items가 참조하던 쪽).
DROP TABLE IF EXISTS public.item_purchases;
DROP TABLE IF EXISTS public.user_items;
DROP TABLE IF EXISTS public.items;
