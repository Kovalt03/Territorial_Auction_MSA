-- Strangler 기간 모놀리식의 기존 BIGSERIAL ID 공간과 충돌하지 않도록 신규 ID 범위를 분리한다.
ALTER SEQUENCE users_id_seq RESTART WITH 1000000000;
