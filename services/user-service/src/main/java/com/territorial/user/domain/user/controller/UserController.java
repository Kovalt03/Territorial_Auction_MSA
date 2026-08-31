package com.territorial.user.domain.user.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.user.domain.user.dto.ChangeNicknameRequest;
import com.territorial.user.domain.user.dto.ChangeNicknameResponse;
import com.territorial.user.domain.user.dto.ChangePasswordRequest;
import com.territorial.user.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신원 프로필 쓰기 — 게이트웨이가 이 경로만 user-service로 라우팅(나머지 /api/v1/users는 모놀리식). */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<ChangeNicknameResponse>> changeNickname(
            @AuthenticationPrincipal Long userId, @RequestBody ChangeNicknameRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.changeNickname(userId, request.nickname())));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long userId, @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
