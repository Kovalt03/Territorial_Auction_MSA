-- =============================================
-- Territorial Auction - Dev Seed Data
-- =============================================
-- 레퍼런스 데이터(territory_grades, continents, territories, island_grades,
-- seasons, items, building_types, season_passes)는 Spring Boot 시작 시
-- 각 Seeder(ApplicationRunner)가 resources/db/*.yml 에서 읽어 자동 삽입합니다.
-- 이 파일에는 개발용 테스트 유저 데이터만 유지합니다.
-- =============================================

-- 테스트 유저 (password: password1!)
INSERT INTO users (username, email, password_hash, nickname)
VALUES (
    'testuser',
    'test@example.com',
    '$2a$10$HHyA9yO4qN0DhT4unVqwOOBUiQDZ6i/OhobZ0g0xFn3rB2IjaqK06',
    '테스트유저'
) ON CONFLICT DO NOTHING;

-- 테스트 유저 지갑 (AP 5000, GP 1000)
INSERT INTO wallets (user_id, available_ap, locked_ap, available_gp)
SELECT id, 5000, 0, 1000 FROM users WHERE username = 'testuser'
ON CONFLICT (user_id) DO UPDATE
    SET available_ap = 5000,
        available_gp = 1000;

-- 테스트 유저 알림 설정
INSERT INTO notification_settings (user_id)
SELECT id FROM users WHERE username = 'testuser'
ON CONFLICT (user_id) DO NOTHING;
