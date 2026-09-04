package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.CombatAdminClient;
import com.territorial.admin.client.CombatAdminClient.UserResourceSnapshot;
import com.territorial.admin.client.MapTerritoryClient;
import com.territorial.admin.client.UserAdminClient;
import com.territorial.admin.client.UserAdminClient.UserView;
import com.territorial.admin.client.UserProvisioningClient;
import com.territorial.admin.client.UserStatus;
import com.territorial.admin.client.WalletClient;
import com.territorial.admin.client.WalletSnapshot;
import com.territorial.admin.domain.admin.dto.AdminAdjustWalletRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkAdjustWalletRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkChangeStatusRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.admin.domain.admin.dto.AdminChangeUserStatusRequest;
import com.territorial.admin.domain.admin.dto.AdminUserDetailResponse;
import com.territorial.admin.domain.admin.dto.AdminUserListResponse;
import com.territorial.admin.domain.admin.dto.AdminUserResponse;
import com.territorial.admin.global.exception.ErrorCode;
import com.territorial.auction.global.exception.CustomException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 유저 관리는 user-service로 위임(신원 소유). 조회는 UserAdminClient, 상태변경은 UserProvisioningClient. 감사 로그는 admin
 * 소유.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserAdminClient userAdminClient;
    private final WalletClient walletClient;
    private final UserProvisioningClient userProvisioningClient;
    private final CombatAdminClient combatAdminClient;
    private final MapTerritoryClient mapTerritoryClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminUserListResponse getUsers(String keyword, UserStatus status, Pageable pageable) {
        UserAdminClient.UserPage page =
                userAdminClient.search(
                        status != null ? status.name() : null,
                        keyword,
                        pageable.getPageNumber(),
                        pageable.getPageSize());
        List<AdminUserResponse> users =
                page.content().stream().map(AdminUserResponse::from).toList();
        return new AdminUserListResponse(page.totalElements(), page.page(), page.size(), users);
    }

    public AdminUserDetailResponse getUser(Long userId) {
        UserView user = findUserOrThrow(userId);
        return toDetail(user, findWallet(userId));
    }

    public AdminUserDetailResponse changeStatus(
            Long adminUserId, Long userId, AdminChangeUserStatusRequest request) {
        UserView user = findUserOrThrow(userId);
        validateStatusChange(user, request.status());

        userProvisioningClient.changeStatus(userId, request.status().name());

        adminAuditLogger.record(
                adminUserId,
                "USER_STATUS_CHANGE",
                "USER",
                userId,
                Map.of(
                        "before", user.status(),
                        "after", request.status(),
                        "reason", request.reason()));
        return toDetail(user, findWallet(userId));
    }

    public AdminUserDetailResponse adjustWallet(
            Long adminUserId, Long userId, AdminAdjustWalletRequest request) {
        UserView user = findUserOrThrow(userId);
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
        if (gpDelta != 0) adjustVaultGp(userId, gpDelta);

        Map<String, Object> detail = new HashMap<>();
        detail.put("apDelta", apDelta);
        detail.put("gpDelta", gpDelta);
        detail.put("reason", request.reason());
        adminAuditLogger.record(adminUserId, "WALLET_ADJUST", "USER", userId, detail);
        return toDetail(user, wallet);
    }

    // 선택된 여러 유저의 재화를 일괄 조정 (all-or-nothing).
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
    public AdminBulkResultResponse bulkChangeStatus(
            Long adminUserId, AdminBulkChangeStatusRequest request) {
        List<Long> userIds = request.userIds().stream().distinct().toList();
        for (Long userId : userIds) {
            UserView user = findUserOrThrow(userId);
            validateStatusChange(user, request.status());

            userProvisioningClient.changeStatus(userId, request.status().name());
            adminAuditLogger.record(
                    adminUserId,
                    "USER_STATUS_CHANGE_BULK",
                    "USER",
                    userId,
                    Map.of(
                            "before", user.status(),
                            "after", request.status(),
                            "reason", request.reason()));
        }
        return new AdminBulkResultResponse(userIds.size());
    }

    private void validateStatusChange(UserView user, UserStatus status) {
        if (status == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.INVALID_USER_STATUS);
        }
        if (user.isAdmin() && status == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }
    }

    private UserView findUserOrThrow(Long userId) {
        UserView user = userAdminClient.get(userId);
        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private WalletSnapshot findWallet(Long userId) {
        return walletClient.getWallet(userId);
    }

    // GP 는 금고에서 관리되므로 관리자 GP 지급/차감도 금고에 반영한다.
    private void adjustVaultGp(Long userId, int gpDelta) {
        combatAdminClient.adjustGp(
                userId, gpDelta, "ADMIN_ADJUST_GP:" + java.util.UUID.randomUUID());
    }

    private AdminUserDetailResponse toDetail(UserView user, WalletSnapshot wallet) {
        List<Long> territoryIds = mapTerritoryClient.getOwnerTerritoryIds(user.userId());
        UserResourceSnapshot resources =
                combatAdminClient.getUserResources(user.userId(), territoryIds);
        return new AdminUserDetailResponse(
                user.userId(),
                user.username(),
                user.nickname(),
                user.email(),
                user.status(),
                user.role(),
                user.createdAt(),
                wallet.availableAp(),
                wallet.lockedAp(),
                resources.availableGp(),
                resources.availableFood(),
                territoryIds.size());
    }
}
