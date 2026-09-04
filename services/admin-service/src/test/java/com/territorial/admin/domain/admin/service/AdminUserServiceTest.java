package com.territorial.admin.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.territorial.admin.domain.admin.dto.AdminBulkChangeStatusRequest;
import com.territorial.admin.domain.admin.dto.AdminChangeUserStatusRequest;
import com.territorial.admin.global.exception.ErrorCode;
import com.territorial.auction.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @InjectMocks private AdminUserService adminUserService;

    @Mock private UserAdminClient userAdminClient;
    @Mock private WalletClient walletClient;
    @Mock private UserProvisioningClient userProvisioningClient;
    @Mock private CombatAdminClient combatAdminClient;
    @Mock private MapTerritoryClient mapTerritoryClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    private UserView user(long id, String status, String role) {
        return new UserView(
                id, "u" + id, "닉" + id, id + "@ta.local", status, role, LocalDateTime.now());
    }

    private void stubDetailCollaborators(long userId) {
        given(walletClient.getWallet(userId)).willReturn(new WalletSnapshot(100, 0));
        given(mapTerritoryClient.getOwnerTerritoryIds(userId)).willReturn(List.of());
        given(combatAdminClient.getUserResources(eq(userId), anyList()))
                .willReturn(new UserResourceSnapshot(50, 30));
    }

    @Test
    void changeStatusDelegatesToUserServiceAndAuditsChange() {
        given(userAdminClient.get(1L)).willReturn(user(1L, "ACTIVE", "USER"));
        stubDetailCollaborators(1L);

        adminUserService.changeStatus(
                9L, 1L, new AdminChangeUserStatusRequest(UserStatus.SUSPENDED, "abuse"));

        verify(userProvisioningClient).changeStatus(1L, "SUSPENDED");
        verify(adminAuditLogger)
                .record(eq(9L), eq("USER_STATUS_CHANGE"), eq("USER"), eq(1L), any());
    }

    @Test
    void changeStatusRejectsWithdrawn() {
        given(userAdminClient.get(1L)).willReturn(user(1L, "ACTIVE", "USER"));

        assertThatThrownBy(
                        () ->
                                adminUserService.changeStatus(
                                        9L,
                                        1L,
                                        new AdminChangeUserStatusRequest(
                                                UserStatus.WITHDRAWN, "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USER_STATUS);
        verify(userProvisioningClient, never()).changeStatus(any(), any());
    }

    @Test
    void changeStatusRejectsSuspendingAdmin() {
        given(userAdminClient.get(1L)).willReturn(user(1L, "ACTIVE", "ADMIN"));

        assertThatThrownBy(
                        () ->
                                adminUserService.changeStatus(
                                        9L,
                                        1L,
                                        new AdminChangeUserStatusRequest(
                                                UserStatus.SUSPENDED, "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_SUSPEND_ADMIN);
    }

    @Test
    void getUserThrowsWhenUserMissing() {
        given(userAdminClient.get(2L)).willReturn(null);

        assertThatThrownBy(() -> adminUserService.getUser(2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void adjustWalletRejectsWhenBothDeltasZero() {
        given(userAdminClient.get(1L)).willReturn(user(1L, "ACTIVE", "USER"));

        assertThatThrownBy(
                        () ->
                                adminUserService.adjustWallet(
                                        9L, 1L, new AdminAdjustWalletRequest(0, 0, "noop")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(walletClient, never()).adjust(any(), anyInt(), any());
    }

    @Test
    void bulkChangeStatusRejectsSuspendingAdmin() {
        given(userAdminClient.get(1L)).willReturn(user(1L, "ACTIVE", "ADMIN"));

        assertThatThrownBy(
                        () ->
                                adminUserService.bulkChangeStatus(
                                        9L,
                                        new AdminBulkChangeStatusRequest(
                                                List.of(1L), UserStatus.SUSPENDED, "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_SUSPEND_ADMIN);
    }
}
