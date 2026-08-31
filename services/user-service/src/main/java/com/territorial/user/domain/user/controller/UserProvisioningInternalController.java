package com.territorial.user.domain.user.controller;

import com.territorial.user.domain.user.dto.OAuthProvisionResult;
import com.territorial.user.domain.user.service.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway로 라우팅하지 않는 신원 프로비저닝 계약. 모놀리식 OAuth 콜백이 호출. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserProvisioningInternalController {

    private final UserProvisioningService userProvisioningService;

    @PostMapping("/provision-oauth")
    public ResponseEntity<OAuthProvisionResult> provisionOAuth(
            @RequestBody ProvisionOAuthRequest request) {
        return ResponseEntity.ok(
                userProvisioningService.provisionOAuth(
                        request.username(), request.email(), request.nickname()));
    }

    public record ProvisionOAuthRequest(String username, String email, String nickname) {}
}
