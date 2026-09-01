package com.territorial.user.domain.user.controller;

import com.territorial.user.domain.user.dto.OAuthProvisionResult;
import com.territorial.user.domain.user.service.UserProvisioningService;
import com.territorial.user.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway로 라우팅하지 않는 신원 프로비저닝·상태 계약. 모놀리식 OAuth 콜백·admin이 호출. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserProvisioningInternalController {

    private final UserProvisioningService userProvisioningService;
    private final UserService userService;

    @PostMapping("/provision-oauth")
    public ResponseEntity<OAuthProvisionResult> provisionOAuth(
            @RequestBody ProvisionOAuthRequest request) {
        return ResponseEntity.ok(
                userProvisioningService.provisionOAuth(
                        request.username(), request.email(), request.nickname()));
    }

    /** 관리자 상태 변경(정지·탈퇴·복구). user-service가 status 소유 → 로그인 차단이 실제로 먹힌다. */
    @PostMapping("/{userId}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long userId, @RequestBody ChangeStatusRequest request) {
        userService.changeStatus(userId, request.status());
        return ResponseEntity.ok().build();
    }

    public record ProvisionOAuthRequest(String username, String email, String nickname) {}

    public record ChangeStatusRequest(String status) {}
}
