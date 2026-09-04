-- admin-service 추출 이후 모놀리식 고아 테이블 정리.
-- 관리 콘솔 설정(공지 등 키-값)과 감사 로그는 admin-service 전용 DB(admin)가 소유하며,
-- 관리자 신원은 admin_accounts로 분리됐다. 모놀리식은 더 이상 domain/admin을 매핑하지 않는다(삭제 완료).
--
-- 두 테이블 모두 외부 FK 참조가 없어 단순 DROP으로 충분하나, 잔존 시퀀스·제약을 함께 제거하려 CASCADE를 쓴다.
DROP TABLE IF EXISTS public.admin_audit_logs CASCADE;
DROP TABLE IF EXISTS public.admin_settings CASCADE;
