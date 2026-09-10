package com.territorial.auction.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionOutboxEventRepository extends JpaRepository<AuctionOutboxEvent, String> {
    List<AuctionOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
