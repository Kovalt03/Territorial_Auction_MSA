package com.territorial.auction.domain.user.client;

/** user-service OAuth 프로비저닝 응답 — 발급 ID와 식별자. 로컬 프로젝션 생성에 사용. */
public record OAuthProvisionResult(Long userId, String username, String nickname, String email) {}
