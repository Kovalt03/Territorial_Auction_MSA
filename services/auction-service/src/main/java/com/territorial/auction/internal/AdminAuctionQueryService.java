package com.territorial.auction.internal;

import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionBid;
import com.territorial.auction.internal.dto.AdminActiveBidListView;
import com.territorial.auction.internal.dto.AdminBidPageView;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모놀리식 admin이 auction 데이터를 조회하기 위한 /internal 질의. 스냅샷 필드만으로 응답을 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuctionQueryService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;

    public long countActiveAuctions() {
        return auctionRepository.countActiveAuctions(LocalDateTime.now());
    }

    public AdminBidPageView getBids(Long bidderId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<AuctionBid> page =
                auctionBidRepository.findAllByBidderIdWithAuction(bidderId, pageable);
        List<AdminBidPageView.Item> bids =
                page.getContent().stream().map(bid -> toBidItem(bid, now)).toList();
        return new AdminBidPageView(
                page.getTotalElements(), page.getNumber(), page.getSize(), bids);
    }

    public AdminActiveBidListView getActiveBids(Long bidderId) {
        LocalDateTime now = LocalDateTime.now();
        List<AdminActiveBidListView.Item> activeBids =
                auctionBidRepository.findLatestBidPerAuctionByBidder(bidderId).stream()
                        .filter(bid -> isOngoing(bid.getAuction(), now))
                        .map(this::toActiveBidItem)
                        .toList();
        return new AdminActiveBidListView(activeBids);
    }

    private AdminBidPageView.Item toBidItem(AuctionBid bid, LocalDateTime now) {
        Auction a = bid.getAuction();
        return new AdminBidPageView.Item(
                a.getId(),
                a.getTerritoryId(),
                a.getCoordX(),
                a.getCoordY(),
                a.getContinentName(),
                a.getGrade(),
                bid.getPrice(),
                a.getCurrentPrice(),
                bid.getBidAt(),
                isOngoing(a, now));
    }

    private AdminActiveBidListView.Item toActiveBidItem(AuctionBid bid) {
        Auction a = bid.getAuction();
        return new AdminActiveBidListView.Item(
                a.getId(),
                a.getTerritoryId(),
                a.getCoordX(),
                a.getCoordY(),
                a.getContinentName(),
                a.getGrade(),
                bid.getPrice(),
                a.getCurrentPrice(),
                bid.getPrice().intValue() == a.getCurrentPrice().intValue(),
                a.getEndAt());
    }

    private boolean isOngoing(Auction a, LocalDateTime now) {
        return !a.isSettled() && a.getEndAt().isAfter(now);
    }
}
