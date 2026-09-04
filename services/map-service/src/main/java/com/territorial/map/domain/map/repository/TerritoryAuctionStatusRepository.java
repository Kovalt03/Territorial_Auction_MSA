package com.territorial.map.domain.map.repository;

import com.territorial.map.domain.map.entity.TerritoryAuctionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryAuctionStatusRepository
        extends JpaRepository<TerritoryAuctionStatus, Long> {

    // 그리드: 주어진 영토들 중 진행 중(endAt 미래)인 경매의 territoryId
    List<TerritoryAuctionStatus> findByTerritoryIdInAndEndAtAfter(
            List<Long> territoryIds, LocalDateTime now);

    // 영토 상세: 해당 영토의 진행 중 경매
    Optional<TerritoryAuctionStatus> findByTerritoryIdAndEndAtAfter(
            Long territoryId, LocalDateTime now);

    // bid 이벤트는 auctionId만 전달 → auctionId로 조회
    Optional<TerritoryAuctionStatus> findByAuctionId(Long auctionId);

    void deleteByAuctionId(Long auctionId);
}
