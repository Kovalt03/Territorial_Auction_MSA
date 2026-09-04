package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.client.CombatAdminClient.UserResourceSnapshot;
import com.territorial.auction.domain.admin.dto.AdminAdjustWalletRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkAdjustWalletRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkChangeStatusRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminChangeUserStatusRequest;
import com.territorial.auction.domain.admin.dto.AdminUserDetailResponse;
import com.territorial.auction.domain.admin.dto.AdminUserListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserResponse;
import com.territorial.auction.domain.user.client.UserProvisioningClient;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.MapTerritoryClient;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final WalletClient walletClient;
    private final UserProvisioningClient userProvisioningClient;
    private final CombatAdminClient combatAdminClient;
    private final MapTerritoryClient mapTerritoryClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminUserListResponse getUsers(String keyword, UserStatus status, Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : "";
        Page<User> page = userRepository.searchForAdmin(status, kw, pageable);
        List<AdminUserResponse> users =
                page.getContent().stream().map(AdminUserResponse::from).toList();
        return new AdminUserListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), users);
    }

    public AdminUserDetailResponse getUser(Long userId) {
        User user = findUserOrThrow(userId);
        return toDetail(user, findWallet(userId));
    }

    @Transactional
    public AdminUserDetailResponse changeStatus(
            Long adminUserId, Long userId, AdminChangeUserStatusRequest request) {
        User user = findUserOrThrow(userId);
        validateStatusChange(user, request.status());

        UserStatus before = user.getStatus();
        user.updateStatus(request.status()); // 프로젝션 즉시 반영(admin 표시)
        // status 소유자(user-service)에 반영해야 로그인 차단이 실제로 먹힌다.
        userProvisioningClient.changeStatus(userId, request.status().name());

        adminAuditLogger.record(
                adminUserId,
                "USER_STATUS_CHANGE",
                "USER",
                userId,
                Map.of("before", before, "after", request.status(), "reason", request.reason()));
        return toDetail(user, findWallet(userId));
    }

    @Transactional
    public AdminUserDetailResponse adjustWallet(
            Long adminUserId, Long userId, AdminAdjustWalletRequest request) {
        User user = findUserOrThrow(userId);
        int apDelta = request.apDelta() != null ? request.apDelta() : 0;
        int gpDelta = request.gpDelta() != null ? request.gpDelta() : 0;
        if (apDelta == 0 && gpDelta == 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        WalletSnapshot wallet =
                apDelta != 0
                        ? walletClient.adjust(
                                userId, apDelta, "ADMIN_ADJUST:" + java.util.UUID.randomUUID())
                        : walletClient.getWallet(userId);
        if (gpDelta != 0) adjustVaultGp(user.getId(), gpDelta);

        Map<String, Object> detail = new HashMap<>();
        detail.put("apDelta", apDelta);
        detail.put("gpDelta", gpDelta);
        detail.put("reason", request.reason());
        adminAuditLogger.record(adminUserId, "WALLET_ADJUST", "USER", userId, detail);
        return toDetail(user, wallet);
    }

    // 선택된 여러 유저의 재화를 일괄 조정. 한 명이라도 실패하면 전체 롤백(all-or-nothing).
    @Transactional
    public AdminBulkResultResponse bulkAdjustWallet(
            Long adminUserId, AdminBulkAdjustWalletRequest request) {
        int apDelta = request.apDelta() != null ? request.apDelta() : 0;
        int gpDelta = request.gpDelta() != null ? request.gpDelta() : 0;
        if (apDelta == 0 && gpDelta == 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        List<Long> userIds = request.userIds().stream().distinct().toList();
        for (Long userId : userIds) {
            if (apDelta != 0) {
                walletClient.adjust(
                        userId, apDelta, "ADMIN_BULK_ADJUST:" + java.util.UUID.randomUUID());
            }
            if (gpDelta != 0) {
                findUserOrThrow(userId);
                adjustVaultGp(userId, gpDelta);
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("apDelta", apDelta);
            detail.put("gpDelta", gpDelta);
            detail.put("reason", request.reason());
            adminAuditLogger.record(adminUserId, "WALLET_ADJUST_BULK", "USER", userId, detail);
        }
        return new AdminBulkResultResponse(userIds.size());
    }

    // 선택된 여러 유저의 상태를 일괄 변경. all-or-nothing.
    @Transactional
    public AdminBulkResultResponse bulkChangeStatus(
            Long adminUserId, AdminBulkChangeStatusRequest request) {
        List<Long> userIds = request.userIds().stream().distinct().toList();
        for (Long userId : userIds) {
            User user = findUserOrThrow(userId);
            validateStatusChange(user, request.status());

            UserStatus before = user.getStatus();
            user.updateStatus(request.status()); // 프로젝션 즉시 반영
            userProvisioningClient.changeStatus(userId, request.status().name());
            adminAuditLogger.record(
                    adminUserId,
                    "USER_STATUS_CHANGE_BULK",
                    "USER",
                    userId,
                    Map.of(
                            "before",
                            before,
                            "after",
                            request.status(),
                            "reason",
                            request.reason()));
        }
        return new AdminBulkResultResponse(userIds.size());
    }

    private void validateStatusChange(User user, UserStatus status) {
        if (status == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.INVALID_USER_STATUS);
        }
        if (user.isAdmin() && status == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private WalletSnapshot findWallet(Long userId) {
        return walletClient.getWallet(userId);
    }

    // GP 는 금고에서 관리되므로 관리자 GP 지급/차감도 금고에 반영한다.
    private void adjustVaultGp(Long userId, int gpDelta) {
        combatAdminClient.adjustGp(
                userId, gpDelta, "ADMIN_ADJUST_GP:" + java.util.UUID.randomUUID());
    }

    private AdminUserDetailResponse toDetail(User user, WalletSnapshot wallet) {
        List<Long> territoryIds = mapTerritoryClient.getOwnerTerritoryIds(user.getId());
        UserResourceSnapshot resources =
                combatAdminClient.getUserResources(user.getId(), territoryIds);
        return new AdminUserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getStatus().name(),
                user.getRole().name(),
                user.getCreatedAt(),
                wallet.availableAp(),
                wallet.lockedAp(),
                resources.availableGp(),
                resources.availableFood(),
                territoryIds.size());
    }
}
