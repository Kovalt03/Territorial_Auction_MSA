package com.territorial.auction.client;

public interface WalletClient {
    BidEscrowResult bidEscrow(BidEscrowRequest request);

    /** escrow 보상: 앞선 bidEscrow를 역전한다. 입찰 로컬 트랜잭션 롤백 시 호출. */
    void compensateBidEscrow(BidEscrowRequest request);

    void consumeLocked(Long winnerId, int finalPrice, Long auctionId);

    void refundLocked(Long bidderId, int amount, Long auctionId);
}
