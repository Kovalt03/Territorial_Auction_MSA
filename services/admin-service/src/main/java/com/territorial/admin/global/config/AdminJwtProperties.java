package com.territorial.admin.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 관리자 전용 JWT 설정. 공개 유저 JWT(user-service)와 **분리된 서명키**를 쓴다(권한 상승 방지). */
@ConfigurationProperties(prefix = "admin-jwt")
public record AdminJwtProperties(
        String secret, long accessTokenValidityMs, long refreshTokenValidityMs) {}
