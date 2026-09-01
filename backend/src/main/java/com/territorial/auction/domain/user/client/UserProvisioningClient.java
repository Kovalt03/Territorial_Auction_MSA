package com.territorial.auction.domain.user.client;

/** user-service 신원 계약(소유 역전) — OAuth 프로비저닝·상태 변경. */
public interface UserProvisioningClient {

    OAuthProvisionResult provisionOAuth(String username, String email, String nickname);

    /** 관리자 상태 변경을 user-service(status 소유자)에 반영 — 로그인 차단이 실제로 먹히게 한다. */
    void changeStatus(Long userId, String status);
}
