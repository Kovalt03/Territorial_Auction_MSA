package com.territorial.auction.domain.admin.dto;

import java.time.LocalDateTime;

/** 관리자용 유저 진행 중 입찰 항목. auction-service /internal 응답을 그대로 역직렬화한다(필드명 일치). */
public record AdminUserActiveBidResponse(
        Long auctionId,
        Long territoryId,
        int coordX,
        int coordY,
        String continentName,
        String grade,
        int myBidPrice,
        int currentPrice,
        boolean topBidder,
        LocalDateTime endAt) {}
