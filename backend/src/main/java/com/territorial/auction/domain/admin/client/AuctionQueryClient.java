package com.territorial.auction.domain.admin.client;

import com.territorial.auction.domain.admin.dto.AdminAuctionListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidListResponse;
import org.springframework.data.domain.Pageable;

/** 분리된 auction-service의 경매 데이터·관리 작업을 admin이 수행하기 위한 포트. 구현체는 /internal REST 호출. */
public interface AuctionQueryClient {

    long countActiveAuctions();

    AdminUserBidListResponse getBids(Long bidderId, Pageable pageable);

    AdminUserActiveBidListResponse getActiveBids(Long bidderId);

    AdminAuctionListResponse getActiveAuctions(Pageable pageable);

    void forceSettle(Long auctionId);

    void forceCancel(Long auctionId);
}
