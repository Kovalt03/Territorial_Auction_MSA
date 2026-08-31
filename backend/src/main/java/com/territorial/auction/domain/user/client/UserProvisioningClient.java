package com.territorial.auction.domain.user.client;

/** OAuth 신원 프로비저닝을 user-service에 위임(소유 역전). username 기준 멱등. */
public interface UserProvisioningClient {

    OAuthProvisionResult provisionOAuth(String username, String email, String nickname);
}
