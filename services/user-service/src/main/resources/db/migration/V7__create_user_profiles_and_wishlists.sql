-- user-BFF 흡수(Stage 8): 모놀 domain/user의 프로필 이미지·위시리스트를 user DB로 이관.
-- 기존 행 데이터는 배포 시 모놀 DB에서 복사한다(별도 이관 스크립트).

CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    profile_image_url VARCHAR(255),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wishlists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    territory_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wishlists_user_territory UNIQUE (user_id, territory_id)
);
