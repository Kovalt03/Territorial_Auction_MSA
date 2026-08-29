package com.territorial.auction.repository;

import com.territorial.auction.entity.AuctionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionHistoryRepository extends JpaRepository<AuctionHistory, Long> {

    // winner·territory 관계 제거됨 — winnerName·territoryId는 스냅샷 필드라 파생 쿼리로 충분
    List<AuctionHistory> findAllByTerritoryIdOrderByWonAtDesc(Long territoryId);
}
