package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.AuctionQueryClient;
import com.territorial.auction.domain.admin.dto.AdminBulkNotificationRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminSendNotificationRequest;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserTerritoryListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserTerritoryResponse;
import com.territorial.auction.domain.notification.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.MapAdminClient;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserActivityService {

    private final UserRepository userRepository;
    private final AuctionQueryClient auctionQueryClient;
    private final MapAdminClient mapAdminClient;
    private final NotificationService notificationService;
    private final AdminAuditLogger adminAuditLogger;

    public AdminUserBidListResponse getBids(Long userId, Pageable pageable) {
        validateUserExists(userId);
        return auctionQueryClient.getBids(userId, pageable);
    }

    public AdminUserActiveBidListResponse getActiveBids(Long userId) {
        validateUserExists(userId);
        return auctionQueryClient.getActiveBids(userId);
    }

    public AdminUserTerritoryListResponse getTerritories(Long userId, Pageable pageable) {
        validateUserExists(userId);
        MapAdminClient.UserTerritoryPage page =
                mapAdminClient.getUserTerritories(
                        userId, pageable.getPageNumber(), pageable.getPageSize());
        List<AdminUserTerritoryResponse> territories =
                page.content().stream().map(AdminUserTerritoryResponse::from).toList();
        return new AdminUserTerritoryListResponse(
                page.totalElements(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                territories);
    }

    @Transactional
    public void sendNotification(
            Long adminUserId, Long userId, AdminSendNotificationRequest request) {
        // sendNotification 내부에서 유저 존재를 검증한다.
        notificationService.sendNotification(
                userId, NotificationType.ADMIN_NOTICE, request.message());
        adminAuditLogger.record(
                adminUserId,
                "USER_NOTIFICATION_SEND",
                "USER",
                userId,
                Map.of("message", request.message()));
    }

    // 선택된 여러 유저에게 알림 일괄 발송. 발송 전 전원 존재를 검증(부분 발송 방지).
    @Transactional
    public AdminBulkResultResponse bulkSendNotification(
            Long adminUserId, AdminBulkNotificationRequest request) {
        List<Long> userIds = request.userIds().stream().distinct().toList();
        if (userRepository.findAllById(userIds).size() != userIds.size()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        for (Long userId : userIds) {
            notificationService.sendNotification(
                    userId, NotificationType.ADMIN_NOTICE, request.message());
            adminAuditLogger.record(
                    adminUserId,
                    "USER_NOTIFICATION_SEND_BULK",
                    "USER",
                    userId,
                    Map.of("message", request.message()));
        }
        return new AdminBulkResultResponse(userIds.size());
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
