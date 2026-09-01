-- user-service 이벤트로 채우는 표시용 유저 프로젝션(닉네임 해소).
CREATE TABLE user_display (
    user_id  BIGINT PRIMARY KEY,
    nickname VARCHAR(30) NOT NULL
);
