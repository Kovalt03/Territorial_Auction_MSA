package com.territorial.realtime.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** WS CONNECT 단계 access token 검증용 — user-service와 공유하는 서명 secret만 필요. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {}
