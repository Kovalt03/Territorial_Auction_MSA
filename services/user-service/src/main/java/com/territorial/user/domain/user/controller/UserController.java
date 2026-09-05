package com.territorial.user.domain.user.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.user.domain.user.dto.ChangeNicknameRequest;
import com.territorial.user.domain.user.dto.ChangeNicknameResponse;
import com.territorial.user.domain.user.dto.ChangePasswordRequest;
import com.territorial.user.domain.user.dto.ChargeApRequest;
import com.territorial.user.domain.user.dto.ChargeApResponse;
import com.territorial.user.domain.user.dto.DeleteMeRequest;
import com.territorial.user.domain.user.dto.MyProfileResponse;
import com.territorial.user.domain.user.dto.MyTerritoryResponse;
import com.territorial.user.domain.user.dto.MyWalletResponse;
import com.territorial.user.domain.user.dto.NotificationSettingResponse;
import com.territorial.user.domain.user.dto.UpdateNotificationSettingRequest;
import com.territorial.user.domain.user.dto.UserProfileResponse;
import com.territorial.user.domain.user.service.PaymentService;
import com.territorial.user.domain.user.service.UserProfileService;
import com.territorial.user.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신원 프로필 읽기·쓰기 + 지갑 표시·AP 충전(BFF). user-service가 /api/v1/users 전체를 서빙한다. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final PaymentService paymentService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getUserProfile(userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMe(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getMyProfile(userId)));
    }

    @GetMapping("/me/territories")
    public ResponseEntity<ApiResponse<MyTerritoryResponse>> getMyTerritories(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(userProfileService.getMyTerritories(userId, pageable)));
    }

    @GetMapping("/me/wallet")
    public ResponseEntity<ApiResponse<MyWalletResponse>> getMyWallet(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getMyWallet(userId)));
    }

    @PostMapping("/me/ap/charge")
    public ResponseEntity<ApiResponse<ChargeApResponse>> chargeAp(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid ChargeApRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.chargeAp(userId, request)));
    }

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

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(
            @AuthenticationPrincipal Long userId,
            @RequestBody DeleteMeRequest request,
            HttpServletRequest httpRequest) {
        userService.deleteMe(userId, request.password(), resolveToken(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    @GetMapping("/me/settings")
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> getNotificationSetting(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getNotificationSetting(userId)));
    }

    @PatchMapping("/me/settings")
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateNotificationSetting(
            @AuthenticationPrincipal Long userId,
            @RequestBody UpdateNotificationSettingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.updateNotificationSetting(userId, request)));
    }
}
