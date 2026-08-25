package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminBulkNotificationRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminSendNotificationRequest;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidResponse;
import com.territorial.auction.domain.admin.dto.AdminUserTerritoryListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserTerritoryResponse;
import com.territorial.auction.domain.auction.entity.AuctionBid;
import com.territorial.auction.domain.auction.repository.AuctionBidRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
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
public class AdminUserActivityService {

    private final UserRepository userRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final TerritoryRepository territoryRepository;
    private final NotificationService notificationService;
    private final AdminAuditLogger adminAuditLogger;

    public AdminUserBidListResponse getBids(Long userId, Pageable pageable) {
        validateUserExists(userId);
        LocalDateTime now = LocalDateTime.now();
        Page<AuctionBid> page = auctionBidRepository.findAllByBidderIdWithAuction(userId, pageable);
        List<AdminUserBidResponse> bids =
                page.getContent().stream().map(b -> AdminUserBidResponse.from(b, now)).toList();
        return new AdminUserBidListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), bids);
    }

    public AdminUserActiveBidListResponse getActiveBids(Long userId) {
        validateUserExists(userId);
        LocalDateTime now = LocalDateTime.now();
        List<AdminUserActiveBidResponse> activeBids =
                auctionBidRepository.findLatestBidPerAuctionByBidder(userId).stream()
                        .filter(
                                b ->
                                        !b.getAuction().isSettled()
                                                && b.getAuction().getEndAt().isAfter(now))
                        .map(AdminUserActiveBidResponse::from)
                        .toList();
        return new AdminUserActiveBidListResponse(activeBids);
    }

    public AdminUserTerritoryListResponse getTerritories(Long userId, Pageable pageable) {
        validateUserExists(userId);
        Page<Territory> page = territoryRepository.findAllByUserId(userId, pageable);
        List<AdminUserTerritoryResponse> territories =
                page.getContent().stream().map(AdminUserTerritoryResponse::from).toList();
        return new AdminUserTerritoryListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), territories);
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
