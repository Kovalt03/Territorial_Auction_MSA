package com.territorial.admin.client;

/** user-service 신원 계약. 관리자 상태 변경(정지/해제)을 status 소유자(user-service)에 반영한다. */
public interface UserProvisioningClient {

    /** 관리자 상태 변경을 user-service(status 소유자)에 반영 — 로그인 차단이 실제로 먹히게 한다. */
    void changeStatus(Long userId, String status);
}
