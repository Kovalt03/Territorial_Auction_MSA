package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.territorial.auction.domain.admin.client.AuctionQueryClient;
import com.territorial.auction.domain.admin.dto.AdminBulkNotificationRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminSendNotificationRequest;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidResponse;
import com.territorial.auction.domain.notification.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.MapAdminClient;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserActivityServiceTest {

    @InjectMocks private AdminUserActivityService adminUserActivityService;

    @Mock private UserRepository userRepository;
    @Mock private AuctionQueryClient auctionQueryClient;
    @Mock private MapAdminClient mapAdminClient;
    @Mock private NotificationService notificationService;
    @Mock private AdminAuditLogger adminAuditLogger;

    private final LocalDateTime now = LocalDateTime.now();

    @Test
    @DisplayName("활성 입찰 조회 → 유저 검증 후 auction-service 위임 결과 반환")
    void getActiveBids_delegatesToClient() {
        given(userRepository.existsById(1L)).willReturn(true);
        AdminUserActiveBidListResponse expected =
                new AdminUserActiveBidListResponse(
                        List.of(
                                new AdminUserActiveBidResponse(
                                        1L,
                                        1L,
                                        1,
                                        2,
                                        "크리오 행성",
                                        "B",
                                        2000,
                                        2000,
                                        true,
                                        now.plusHours(1))));
        given(auctionQueryClient.getActiveBids(1L)).willReturn(expected);

        AdminUserActiveBidListResponse res = adminUserActivityService.getActiveBids(1L);

        assertThat(res).isSameAs(expected);
        assertThat(res.activeBids().get(0).topBidder()).isTrue();
    }

    @Test
    @DisplayName("없는 유저의 입찰 조회 → USER_NOT_FOUND")
    void getActiveBids_userNotFound() {
        given(userRepository.existsById(9L)).willReturn(false);

        assertThatThrownBy(() -> adminUserActivityService.getActiveBids(9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 발송 → NotificationService 호출 + 감사 로그 기록")
    void sendNotification_success() {
        adminUserActivityService.sendNotification(
                10L, 1L, new AdminSendNotificationRequest("점검 안내입니다."));

        then(notificationService)
                .should()
                .sendNotification(eq(1L), eq(NotificationType.ADMIN_NOTICE), eq("점검 안내입니다."));
        then(adminAuditLogger)
                .should()
                .record(
                        eq(10L),
                        eq("USER_NOTIFICATION_SEND"),
                        eq("USER"),
                        eq(1L),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("일괄 알림 발송 → 전원에게 발송 + affected 반환")
    void bulkSendNotification_success() {
        User u1 =
                User.builder().username("a").email("a@x").passwordHash("h").nickname("na").build();
        User u2 =
                User.builder().username("b").email("b@x").passwordHash("h").nickname("nb").build();
        given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(u1, u2));

        AdminBulkResultResponse res =
                adminUserActivityService.bulkSendNotification(
                        10L, new AdminBulkNotificationRequest(List.of(1L, 2L), "점검 공지"));

        assertThat(res.affected()).isEqualTo(2);
        then(notificationService)
                .should(times(2))
                .sendNotification(
                        org.mockito.ArgumentMatchers.any(),
                        eq(NotificationType.ADMIN_NOTICE),
                        eq("점검 공지"));
    }

    @Test
    @DisplayName("일괄 알림 대상 중 없는 유저 → USER_NOT_FOUND (발송 안 함)")
    void bulkSendNotification_userNotFound() {
        User u1 =
                User.builder().username("a").email("a@x").passwordHash("h").nickname("na").build();
        given(userRepository.findAllById(List.of(1L, 9L))).willReturn(List.of(u1));

        assertThatThrownBy(
                        () ->
                                adminUserActivityService.bulkSendNotification(
                                        10L,
                                        new AdminBulkNotificationRequest(List.of(1L, 9L), "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(notificationService).shouldHaveNoInteractions();
    }
}
