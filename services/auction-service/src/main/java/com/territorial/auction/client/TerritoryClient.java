package com.territorial.auction.client;

import java.time.LocalDateTime;

public interface TerritoryClient {
    void occupy(
            Long territoryId,
            Long winnerId,
            LocalDateTime occupiedUntil,
            LocalDateTime protectedUntil);

    void release(Long territoryId, LocalDateTime nextAuctionAt);
}
