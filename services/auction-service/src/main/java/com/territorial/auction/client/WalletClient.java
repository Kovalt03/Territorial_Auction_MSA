package com.territorial.auction.client;

public interface WalletClient {
    BidEscrowResult bidEscrow(BidEscrowRequest request);

    void consumeLocked(Long winnerId, int finalPrice, Long auctionId);
}
