package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.client.CombatAdminClient.UserResourceSnapshot;
import com.territorial.auction.domain.admin.dto.AdminAdjustWalletRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkAdjustWalletRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkChangeStatusRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminChangeUserStatusRequest;
import com.territorial.auction.domain.admin.dto.AdminUserDetailResponse;
import com.territorial.auction.domain.admin.dto.AdminUserListResponse;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.client.UserProvisioningClient;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserRole;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @InjectMocks private AdminUserService adminUserService;

    @Mock private UserRepository userRepository;
    @Mock private WalletClient walletClient;
    @Mock private UserProvisioningClient userProvisioningClient;

    @Mock private CombatAdminClient combatAdminClient;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private Territory territory(long id) {
        Territory territory = mock(Territory.class);
        given(territory.getId()).willReturn(id);
        return territory;
    }

    private User user(long id, UserStatus status, UserRole role) {
        User u =
                User.builder()
                        .username("u" + id)
                        .email("e" + id + "@x.com")
                        .passwordHash("h")
                        .nickname("n" + id)
                        .build();
        ReflectionTestUtils.setField(u, "id", id);
        u.updateStatus(status);
        u.updateRole(role);
        return u;
    }

    @Nested
    @DisplayName("getUsers()")
    class GetUsers {

        @Test
        @DisplayName("검색 결과를 목록 엔벨로프로 변환")
        void getUsers_mapsToEnvelope() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            PageRequest pageable = PageRequest.of(0, 20);
            given(userRepository.searchForAdmin(null, "", pageable))
                    .willReturn(new PageImpl<>(List.of(u), pageable, 1));

            AdminUserListResponse res = adminUserService.getUsers(null, null, pageable);

            assertThat(res.totalCount()).isEqualTo(1);
            assertThat(res.page()).isEqualTo(0);
            assertThat(res.users()).hasSize(1);
            assertThat(res.users().get(0).userId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("getUser()")
    class GetUser {

        @Test
        @DisplayName("상세 조회 성공 → 지갑·영토 수 집계")
        void getUser_success() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u));
            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(500, 0));
            List<Territory> territories = List.of(territory(11L), territory(12L), territory(13L));
            given(territoryRepository.findByOwnerId(1L)).willReturn(territories);
            given(combatAdminClient.getUserResources(1L, List.of(11L, 12L, 13L)))
                    .willReturn(new UserResourceSnapshot(30, 40));

            AdminUserDetailResponse res = adminUserService.getUser(1L);

            assertThat(res.availableAp()).isEqualTo(500);
            assertThat(res.availableGp()).isEqualTo(30);
            assertThat(res.territoryCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("없는 유저 → USER_NOT_FOUND")
        void getUser_notFound() {
            given(userRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.getUser(9L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("changeStatus()")
    class ChangeStatus {

        @Test
        @DisplayName("정지 성공 → status SUSPENDED 반영")
        void suspend_success() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u));
            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(0, 0));
            given(territoryRepository.findByOwnerId(1L)).willReturn(List.of());
            given(combatAdminClient.getUserResources(1L, List.of()))
                    .willReturn(new UserResourceSnapshot(0, 0));

            AdminUserDetailResponse res =
                    adminUserService.changeStatus(
                            10L,
                            1L,
                            new AdminChangeUserStatusRequest(UserStatus.SUSPENDED, "약관 위반"));

            assertThat(res.status()).isEqualTo("SUSPENDED");
            then(adminAuditLogger).should().record(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("관리자 계정 정지 시도 → CANNOT_SUSPEND_ADMIN")
        void suspendAdmin_rejected() {
            User admin = user(2L, UserStatus.ACTIVE, UserRole.ADMIN);
            given(userRepository.findById(2L)).willReturn(Optional.of(admin));

            assertThatThrownBy(
                            () ->
                                    adminUserService.changeStatus(
                                            10L,
                                            2L,
                                            new AdminChangeUserStatusRequest(
                                                    UserStatus.SUSPENDED, "사유")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }

        @Test
        @DisplayName("WITHDRAWN으로 변경 시도 → INVALID_USER_STATUS")
        void withdrawn_rejected() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u));

            assertThatThrownBy(
                            () ->
                                    adminUserService.changeStatus(
                                            10L,
                                            1L,
                                            new AdminChangeUserStatusRequest(
                                                    UserStatus.WITHDRAWN, "사유")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_USER_STATUS);
        }
    }

    @Nested
    @DisplayName("adjustWallet()")
    class AdjustWallet {

        @Test
        @DisplayName("AP 차감·GP 지급 성공")
        void adjust_success() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u));
            given(walletClient.adjust(eq(1L), eq(-200), anyString()))
                    .willReturn(new WalletSnapshot(800, 0));
            given(combatAdminClient.adjustGp(eq(1L), eq(100), anyString())).willReturn(150);
            given(territoryRepository.findByOwnerId(1L)).willReturn(List.of());
            given(combatAdminClient.getUserResources(1L, List.of()))
                    .willReturn(new UserResourceSnapshot(150, 0));

            AdminUserDetailResponse res =
                    adminUserService.adjustWallet(
                            10L, 1L, new AdminAdjustWalletRequest(-200, 100, "보상"));

            assertThat(res.availableAp()).isEqualTo(800);
            assertThat(res.availableGp()).isEqualTo(150);
            then(adminAuditLogger).should().record(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("잔액보다 큰 AP 차감 → INSUFFICIENT_AP")
        void adjust_insufficientAp() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u));
            willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                    .given(walletClient)
                    .adjust(eq(1L), eq(-500), anyString());

            assertThatThrownBy(
                            () ->
                                    adminUserService.adjustWallet(
                                            10L,
                                            1L,
                                            new AdminAdjustWalletRequest(-500, null, "사유")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }

        @Test
        @DisplayName("변화량이 모두 0 → INVALID_INPUT, 지갑 미조회")
        void adjust_bothZero() {
            User u = user(1L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u));

            assertThatThrownBy(
                            () ->
                                    adminUserService.adjustWallet(
                                            10L, 1L, new AdminAdjustWalletRequest(0, 0, "사유")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
            then(walletClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("bulk 조작")
    class BulkOps {

        @Test
        @DisplayName("여러 유저 재화 일괄 조정 성공 → affected 반환")
        void bulkAdjustWallet_success() {
            User u1 = user(1L, UserStatus.ACTIVE, UserRole.USER);
            User u2 = user(2L, UserStatus.ACTIVE, UserRole.USER);
            given(userRepository.findById(1L)).willReturn(Optional.of(u1));
            given(userRepository.findById(2L)).willReturn(Optional.of(u2));
            given(combatAdminClient.adjustGp(eq(1L), eq(50), anyString())).willReturn(50);
            given(combatAdminClient.adjustGp(eq(2L), eq(50), anyString())).willReturn(50);

            AdminBulkResultResponse res =
                    adminUserService.bulkAdjustWallet(
                            10L,
                            new AdminBulkAdjustWalletRequest(List.of(1L, 2L), -100, 50, "이벤트"));

            assertThat(res.affected()).isEqualTo(2);
        }

        @Test
        @DisplayName("한 명이라도 잔액 부족 → INSUFFICIENT_AP (전체 롤백)")
        void bulkAdjustWallet_oneInsufficient() {
            User u1 = user(1L, UserStatus.ACTIVE, UserRole.USER);
            User u2 = user(2L, UserStatus.ACTIVE, UserRole.USER);
            given(walletClient.adjust(eq(1L), eq(-100), anyString()))
                    .willReturn(new WalletSnapshot(900, 0));
            willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                    .given(walletClient)
                    .adjust(eq(2L), eq(-100), anyString());

            assertThatThrownBy(
                            () ->
                                    adminUserService.bulkAdjustWallet(
                                            10L,
                                            new AdminBulkAdjustWalletRequest(
                                                    List.of(1L, 2L), -100, 0, "x")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }

        @Test
        @DisplayName("여러 유저 상태 일괄 변경 성공")
        void bulkChangeStatus_success() {
            given(userRepository.findById(1L))
                    .willReturn(Optional.of(user(1L, UserStatus.ACTIVE, UserRole.USER)));
            given(userRepository.findById(2L))
                    .willReturn(Optional.of(user(2L, UserStatus.ACTIVE, UserRole.USER)));

            AdminBulkResultResponse res =
                    adminUserService.bulkChangeStatus(
                            10L,
                            new AdminBulkChangeStatusRequest(
                                    List.of(1L, 2L), UserStatus.SUSPENDED, "제재"));

            assertThat(res.affected()).isEqualTo(2);
        }

        @Test
        @DisplayName("대상에 관리자 포함 시 정지 → CANNOT_SUSPEND_ADMIN")
        void bulkChangeStatus_containsAdmin() {
            given(userRepository.findById(1L))
                    .willReturn(Optional.of(user(1L, UserStatus.ACTIVE, UserRole.USER)));
            given(userRepository.findById(2L))
                    .willReturn(Optional.of(user(2L, UserStatus.ACTIVE, UserRole.ADMIN)));

            assertThatThrownBy(
                            () ->
                                    adminUserService.bulkChangeStatus(
                                            10L,
                                            new AdminBulkChangeStatusRequest(
                                                    List.of(1L, 2L), UserStatus.SUSPENDED, "x")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }
    }
}
