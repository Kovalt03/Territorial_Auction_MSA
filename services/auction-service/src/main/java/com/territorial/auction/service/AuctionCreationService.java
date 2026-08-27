package com.territorial.auction.service;

import com.territorial.auction.AuctionPolicy;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionBid;
import com.territorial.auction.event.TerritoryAuctionReadyEvent;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCreationService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;

    /** map의 territory.auction-ready 이벤트로 경매 생성. 중복 이벤트에 대비해 진행 중 경매가 있으면 스킵(idempotent). */
    @Transactional
    public void createAuction(TerritoryAuctionReadyEvent event) {
        if (auctionRepository
                .findFirstByTerritoryIdAndSettledFalseOrderByEndAtDesc(event.territoryId())
                .isPresent()) {
            return;
        }

        int startingPrice =
                AuctionPolicy.GRADE_START_PRICES.getOrDefault(
                        event.grade(), AuctionPolicy.DEFAULT_START_PRICE);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = now.plusHours(AuctionPolicy.AUCTION_DURATION_HOURS);
        LocalDateTime maxExtendUntil = endAt.plusMinutes(AuctionPolicy.MAX_EXTEND_UNTIL_MINUTES);

        Auction auction =
                Auction.builder()
                        .territoryId(event.territoryId())
                        .coordX(event.coordX())
                        .coordY(event.coordY())
                        .continentName(event.continentName())
                        .continentId(event.continentId())
                        .grade(event.grade())
                        .currentPrice(startingPrice)
                        .startAt(now)
                        .endAt(endAt)
                        .maxExtendUntil(maxExtendUntil)
                        .build();
        auctionRepository.save(auction);

        // 시작가 레코드 (bidder 없음 = 그래프 상 시작점)
        auctionBidRepository.save(
                AuctionBid.builder().auction(auction).price(startingPrice).build());

        log.info(
                "[AuctionCreation] 경매 생성 territoryId={} startingPrice={}",
                event.territoryId(),
                startingPrice);
    }
}
