package com.territorial.user.domain.user.dto;

import com.territorial.user.domain.user.entity.User;

/** OAuth 프로비저닝 결과 — 모놀리식이 로컬 프로젝션을 만들 때 사용할 user-service 발급 ID/식별자. */
public record OAuthProvisionResult(Long userId, String username, String nickname, String email) {

    public static OAuthProvisionResult from(User user) {
        return new OAuthProvisionResult(
                user.getId(), user.getUsername(), user.getNickname(), user.getEmail());
    }
}
