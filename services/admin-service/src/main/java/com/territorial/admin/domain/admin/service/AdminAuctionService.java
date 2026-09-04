package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.AuctionQueryClient;
import com.territorial.admin.domain.admin.dto.AdminAuctionListResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 경매 관리. 경매 데이터·강제 작업은 auction-service가 소유하므로 /internal 클라이언트로 위임하고, 관리자 인증·감사 로그는 모놀리식이 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuctionService {

    private final AuctionQueryClient auctionQueryClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminAuctionListResponse getActiveAuctions(Pageable pageable) {
        return auctionQueryClient.getActiveAuctions(pageable);
    }

    @Transactional
    public void forceSettle(Long adminUserId, Long auctionId) {
        auctionQueryClient.forceSettle(auctionId);
        adminAuditLogger.record(
                adminUserId,
                "AUCTION_FORCE_SETTLE",
                "AUCTION",
                auctionId,
                Map.of("auctionId", auctionId));
    }

    @Transactional
    public void forceCancel(Long adminUserId, Long auctionId) {
        auctionQueryClient.forceCancel(auctionId);
        adminAuditLogger.record(
                adminUserId,
                "AUCTION_FORCE_CANCEL",
                "AUCTION",
                auctionId,
                Map.of("auctionId", auctionId));
    }
}
