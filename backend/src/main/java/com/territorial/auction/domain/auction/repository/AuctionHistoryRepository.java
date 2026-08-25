package com.territorial.auction.domain.auction.repository;

import com.territorial.auction.domain.auction.entity.AuctionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionHistoryRepository extends JpaRepository<AuctionHistory, Long> {

    @Query(
            "SELECT ah FROM AuctionHistory ah"
                    + " JOIN FETCH ah.winner"
                    + " WHERE ah.territory.id = :territoryId"
                    + " ORDER BY ah.wonAt DESC")
    List<AuctionHistory> findAllByTerritoryIdOrderByWonAtDesc(
            @Param("territoryId") Long territoryId);
}
