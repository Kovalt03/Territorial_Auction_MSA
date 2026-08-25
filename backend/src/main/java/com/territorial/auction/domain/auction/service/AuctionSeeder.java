package com.territorial.auction.domain.auction.service;

import com.territorial.auction.domain.auction.AuctionPolicy;
import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.repository.AuctionRepository;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class AuctionSeeder implements ApplicationRunner {

    private static final int AUCTIONS_PER_CONTINENT = 5;

    private final AuctionRepository auctionRepository;
    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (auctionRepository.count() > 0) {
            log.info("auctions 이미 존재 — 건너뜀");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = now.plusHours(AuctionPolicy.AUCTION_DURATION_HOURS);
        LocalDateTime maxExtendUntil = endAt.plusMinutes(AuctionPolicy.MAX_EXTEND_UNTIL_MINUTES);

        List<Auction> auctions = new ArrayList<>();

        for (Continent continent : continentRepository.findAll()) {
            List<Territory> territories =
                    territoryRepository.findAllByContinentId(continent.getId());
            int limit = Math.min(AUCTIONS_PER_CONTINENT, territories.size());
            for (int i = 0; i < limit; i++) {
                Territory territory = territories.get(i);
                String grade = territory.getGrade() != null ? territory.getGrade().getGrade() : "";
                int startPrice =
                        AuctionPolicy.GRADE_START_PRICES.getOrDefault(
                                grade, AuctionPolicy.DEFAULT_START_PRICE);

                territory.startBidding();
                auctions.add(
                        Auction.builder()
                                .territory(territory)
                                .currentPrice(startPrice)
                                .startAt(now.minusHours(1))
                                .endAt(endAt)
                                .maxExtendUntil(maxExtendUntil)
                                .build());
            }
        }

        auctionRepository.saveAll(auctions);
        log.info("auction 시드 완료. 경매 건수={}", auctions.size());
    }
}
