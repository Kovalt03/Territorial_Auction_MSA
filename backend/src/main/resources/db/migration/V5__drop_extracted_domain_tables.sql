-- 추출된 도메인의 모놀리식 고아 테이블 정리.
-- auction / social(+guild) / notification 저장 / user AP지갑·알림설정은 각 서비스 DB가 소유하므로
-- 모놀리식은 더 이상 이 테이블들을 매핑하지 않는다(고아, 대부분 0행).
-- 유지: users·user_profiles(user-service 프로젝션, 15도메인 FK), map·season·item·admin(모놀리식 소유).
-- combat(military·building) 테이블은 V4에서 이미 제거됨.

-- auction-service 소유 (자식 → 부모 순)
DROP TABLE IF EXISTS public.auction_bids;
DROP TABLE IF EXISTS public.auction_histories;
DROP TABLE IF EXISTS public.auctions;

-- social-service 소유 (채팅·길드·관심그룹)
DROP TABLE IF EXISTS public.chat_messages;
DROP TABLE IF EXISTS public.chat_rooms;
DROP TABLE IF EXISTS public.guild_members;
DROP TABLE IF EXISTS public.guilds;
DROP TABLE IF EXISTS public.interest_groups;

-- notification-service 소유 (알림 저장)
DROP TABLE IF EXISTS public.notification_logs;

-- user-service 소유 (AP 지갑·알림 설정)
DROP TABLE IF EXISTS public.wallets;
DROP TABLE IF EXISTS public.notification_settings;
