package com.territorial.auction.domain.admin.client;

import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidListResponse;
import org.springframework.data.domain.Pageable;

/** 분리된 auction-service의 경매 데이터를 admin이 조회하기 위한 포트. 구현체는 /internal REST 호출. */
public interface AuctionQueryClient {

    long countActiveAuctions();

    AdminUserBidListResponse getBids(Long bidderId, Pageable pageable);

    AdminUserActiveBidListResponse getActiveBids(Long bidderId);
}
