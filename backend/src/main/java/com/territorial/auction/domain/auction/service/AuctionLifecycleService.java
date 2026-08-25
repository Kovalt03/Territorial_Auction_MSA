package com.territorial.auction.domain.auction.service;

import com.territorial.auction.domain.admin.dto.AdminAuctionListResponse;
import com.territorial.auction.domain.admin.dto.AdminAuctionResponse;
import com.territorial.auction.domain.admin.entity.AdminSetting;
import com.territorial.auction.domain.admin.repository.AdminSettingRepository;
import com.territorial.auction.domain.admin.service.AdminAuditLogger;
import com.territorial.auction.domain.auction.AuctionPolicy;
import com.territorial.auction.domain.auction.dto.AuctionResultAlert;
import com.territorial.auction.domain.auction.entity.Auction;
import com.territorial.auction.domain.auction.entity.AuctionBid;
import com.territorial.auction.domain.auction.entity.AuctionHistory;
import com.territorial.auction.domain.auction.repository.AuctionBidRepository;
import com.territorial.auction.domain.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.domain.auction.repository.AuctionRepository;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.map.dto.MapUpdateBroadcast;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.event.TerritoryLostEvent;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldClosedEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldStartedEvent;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionLifecycleService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionHistoryRepository auctionHistoryRepository;
    private final TerritoryRepository territoryRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final WalletRepository walletRepository;
    private final SeasonRepository seasonRepository;
    private final AdminSettingRepository adminSettingRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // ── 관리자 강제 종료 ─────────────────────────────────────────────────────────

    /** 진행 중(미정산) 경매 목록 — 관리자 강제 종료 대상 선택용 */
    public AdminAuctionListResponse getActiveAuctionsForAdmin(Pageable pageable) {
        Page<Auction> page = auctionRepository.findActiveForAdmin(LocalDateTime.now(), pageable);
        List<AdminAuctionResponse> auctions =
                page.getContent().stream().map(AdminAuctionResponse::from).toList();
        return new AdminAuctionListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), auctions);
    }

    /** 강제 낙찰: 현재 최고 입찰자에게 즉시 낙찰(기존 정산 로직 재사용). 입찰자 없으면 거부. */
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void forceSettle(Long adminUserId, Long auctionId) {
        Auction auction = findUnsettledOrThrow(auctionId);
        if (auction.getCurrentBidder() == null) {
            throw new CustomException(ErrorCode.AUCTION_NO_BIDDER_TO_SETTLE);
        }
        settleAuction(auction, LocalDateTime.now());
        adminAuditLogger.record(
                adminUserId,
                "AUCTION_FORCE_SETTLE",
                "AUCTION",
                auctionId,
                Map.of(
                        "territoryId", auction.getTerritory().getId(),
                        "winnerId", auction.getCurrentBidder().getId(),
                        "finalPrice", auction.getCurrentPrice()));
    }

    /** 강제 취소: 현재 입찰자 AP 잠금 해제 + 영토 IDLE 복귀(표준 재경매 지연) + 경매 종료. */
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void forceCancel(Long adminUserId, Long auctionId) {
        Auction auction = findUnsettledOrThrow(auctionId);
        LocalDateTime now = LocalDateTime.now();
        Territory territory = auction.getTerritory();
        User bidder = auction.getCurrentBidder();
        if (bidder != null) {
            walletRepository
                    .findByIdWithLock(bidder.getId())
                    .ifPresent(w -> w.refundLockedAp(auction.getCurrentPrice()));
        }
        territory.release(now.plusHours(AuctionPolicy.IDLE_REAUCTION_DELAY_HOURS));
        auction.settle();

        final long finalTerritoryId = territory.getId();
        final int finalCoordX = territory.getCoordX();
        final int finalCoordY = territory.getCoordY();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/sub/map/update",
                                new MapUpdateBroadcast(
                                        finalTerritoryId,
                                        finalCoordX,
                                        finalCoordY,
                                        null,
                                        null,
                                        "IDLE"));
                    }
                });

        adminAuditLogger.record(
                adminUserId,
                "AUCTION_FORCE_CANCEL",
                "AUCTION",
                auctionId,
                Map.of(
                        "territoryId", territory.getId(),
                        "refundedBidderId", bidder != null ? bidder.getId() : -1L,
                        "refundedAp", bidder != null ? auction.getCurrentPrice() : 0));
        log.info("[AuctionLifecycle] 관리자 강제 취소 auctionId={}", auctionId);
    }

    private Auction findUnsettledOrThrow(Long auctionId) {
        Auction auction =
                auctionRepository
                        .findByIdWithDetails(auctionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
        if (auction.isSettled()) {
            throw new CustomException(ErrorCode.AUCTION_ALREADY_SETTLED);
        }
        return auction;
    }

    /** 종료된 미정산 경매를 일괄 정산 */
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void settlePendingAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> expired = auctionRepository.findAllExpiredUnsettled(now);
        for (Auction auction : expired) {
            try {
                settleAuction(auction, now);
            } catch (Exception e) {
                log.error("[AuctionLifecycle] 경매 정산 실패 auctionId={}", auction.getId(), e);
            }
        }
    }

    /** 점유 기간이 만료된 영토를 IDLE로 전환 */
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void releaseExpiredTerritories() {
        LocalDateTime now = LocalDateTime.now();
        List<Territory> expired =
                territoryRepository.findAllExpiredOccupied(Territory.TerritoryStatus.OCCUPIED, now);
        Optional<Season> seasonOpt = seasonRepository.findActiveSeason(now);
        for (Territory territory : expired) {
            final long finalRelTerritoryId = territory.getId();
            final int finalRelCoordX = territory.getCoordX();
            final int finalRelCoordY = territory.getCoordY();
            publishHoldClosedEvent(territory, seasonOpt, now);
            if (territory.getOwner() != null) {
                eventPublisher.publishEvent(
                        new TerritoryLostEvent(territory.getId(), territory.getOwner().getId()));
            }
            // 점유 만료 즉시 재경매 예약
            territory.release(now);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            messagingTemplate.convertAndSend(
                                    "/sub/map/update",
                                    new MapUpdateBroadcast(
                                            finalRelTerritoryId,
                                            finalRelCoordX,
                                            finalRelCoordY,
                                            null,
                                            null,
                                            "IDLE"));
                        }
                    });
            log.info("[AuctionLifecycle] 영토 점유 만료 territoryId={}", territory.getId());
        }
    }

    private void publishHoldClosedEvent(
            Territory territory, Optional<Season> seasonOpt, LocalDateTime now) {
        if (seasonOpt.isEmpty() || territory.getOwner() == null) return;
        eventPublisher.publishEvent(
                new TerritoryHoldClosedEvent(
                        territory.getOwner().getId(),
                        seasonOpt.get().getId(),
                        territory.getId(),
                        now));
    }

    /** nextAuctionAt이 도달한 IDLE 영토에 신규 경매 생성 */
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void createPendingAuctions() {
        if (!isGlobalAuctionEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Territory> ready =
                territoryRepository.findAllReadyForAuction(Territory.TerritoryStatus.IDLE, now);
        for (Territory territory : ready) {
            try {
                createAuction(territory, now);
            } catch (Exception e) {
                log.error("[AuctionLifecycle] 경매 생성 실패 territoryId={}", territory.getId(), e);
            }
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    // 낙찰 영토에 성을 자동 배치한다. 재점유로 이미 성이 있으면 건너뛴다. 초기 stored_gp 는 0 —
    // 영토 수입·성 생산으로 채운다.
    private void createInitialCastle(Territory territory) {
        if (buildingInstanceRepository.existsCastleOnTerritory(territory.getId())) {
            return;
        }
        BuildingType castleType =
                buildingTypeRepository
                        .findByName("CASTLE")
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
        int center = (territory.getGrade().getGridSize() / 2) - 1;
        buildingInstanceRepository.save(
                BuildingInstance.builder()
                        .territory(territory)
                        .buildingType(castleType)
                        .posX(center)
                        .posY(center)
                        .hp(castleType.getMaxHp())
                        .zone(1)
                        .build());
    }

    // 전역 마스터 스위치. 설정 행이 없으면 활성으로 간주한다.
    private boolean isGlobalAuctionEnabled() {
        return adminSettingRepository
                .findBySettingKey(AdminSetting.KEY_AUCTION_ENABLED)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(true);
    }

    private void settleAuction(Auction auction, LocalDateTime now) {
        Territory territory = auction.getTerritory();
        User winner = auction.getCurrentBidder();

        if (winner != null) {
            LocalDateTime occupiedUntil = now.plusDays(AuctionPolicy.OCCUPATION_DURATION_DAYS);
            LocalDateTime protectedUntil = now.plusHours(AuctionPolicy.PROTECTION_DURATION_HOURS);
            territory.occupy(winner, occupiedUntil, protectedUntil);

            // 성이 기본 저장 기능을 가지므로, 낙찰 영토에는 항상 성이 있어야 첫 건물을 지을 수 있다.
            createInitialCastle(territory);

            // 낙찰자 lockedAp 소비
            walletRepository
                    .findByIdWithLock(winner.getId())
                    .ifPresent(wallet -> wallet.consumeLockedAp(auction.getCurrentPrice()));

            Optional<Season> seasonOpt = seasonRepository.findActiveSeason(now);
            Season season = seasonOpt.orElse(null);

            auctionHistoryRepository.save(
                    AuctionHistory.builder()
                            .auction(auction)
                            .territory(territory)
                            .winner(winner)
                            .finalPrice(auction.getCurrentPrice())
                            .wonAt(now)
                            .season(season)
                            .build());

            publishSettlementEvents(winner, season, auction, territory, now);

            final long finalAuctionId = auction.getId();
            final long finalTerritoryId = territory.getId();
            final int finalCoordX = territory.getCoordX();
            final int finalCoordY = territory.getCoordY();
            final int finalPrice = auction.getCurrentPrice();
            final long finalWinnerId = winner.getId();
            final String finalWinnerNickname = winner.getNickname();
            List<Long> runnerUpIds =
                    auctionBidRepository.findDistinctBidderIdsExcluding(
                            auction.getId(), winner.getId());
            final List<Long> finalRunnerUpIds = List.copyOf(runnerUpIds);

            notifyAuctionResult(
                    winner.getId(), finalRunnerUpIds, finalCoordX, finalCoordY, finalPrice);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            messagingTemplate.convertAndSend(
                                    "/sub/user/" + finalWinnerId + "/auction-result",
                                    new AuctionResultAlert(
                                            finalAuctionId,
                                            finalTerritoryId,
                                            finalCoordX,
                                            finalCoordY,
                                            finalPrice,
                                            "WIN"));
                            messagingTemplate.convertAndSend(
                                    "/sub/map/update",
                                    new MapUpdateBroadcast(
                                            finalTerritoryId,
                                            finalCoordX,
                                            finalCoordY,
                                            finalWinnerId,
                                            finalWinnerNickname,
                                            "OCCUPIED"));
                            AuctionResultAlert loseAlert =
                                    new AuctionResultAlert(
                                            finalAuctionId,
                                            finalTerritoryId,
                                            finalCoordX,
                                            finalCoordY,
                                            finalPrice,
                                            "LOSE");
                            for (Long runnerUpId : finalRunnerUpIds) {
                                messagingTemplate.convertAndSend(
                                        "/sub/user/" + runnerUpId + "/auction-result", loseAlert);
                            }
                        }
                    });

            log.info(
                    "[AuctionLifecycle] 낙찰 정산 auctionId={} winner={} price={}",
                    auction.getId(),
                    winner.getNickname(),
                    auction.getCurrentPrice());
        } else {
            // 무낙찰: 일정 시간 후 재경매
            LocalDateTime nextAuctionAt = now.plusHours(AuctionPolicy.IDLE_REAUCTION_DELAY_HOURS);
            territory.release(nextAuctionAt);

            log.info(
                    "[AuctionLifecycle] 무낙찰 정산 auctionId={} nextAuctionAt={}",
                    auction.getId(),
                    nextAuctionAt);
        }

        auction.settle();
    }

    // 낙찰자엔 WIN, 차순위 입찰자 전원엔 LOSE 알림을 알림함에 남긴다(실시간 토스트와 별개로 이력 보존).
    private void notifyAuctionResult(
            Long winnerId, List<Long> runnerUpIds, int coordX, int coordY, int finalPrice) {
        String coord = "(" + coordX + ", " + coordY + ")";
        notificationService.sendNotification(
                winnerId,
                NotificationType.AUCTION_WIN,
                coord + " 영토를 낙찰받았습니다! 낙찰가 " + finalPrice + " AP.");
        for (Long runnerUpId : runnerUpIds) {
            notificationService.sendNotification(
                    runnerUpId, NotificationType.AUCTION_LOSE, coord + " 영토 경매에서 낙찰에 실패했습니다.");
        }
    }

    private void publishSettlementEvents(
            User winner, Season season, Auction auction, Territory territory, LocalDateTime now) {
        if (season == null) return;
        Long seasonId = season.getId();
        String grade = territory.getGrade() != null ? territory.getGrade().getGrade() : "D";

        eventPublisher.publishEvent(
                new AuctionSettledEvent(winner.getId(), seasonId, auction.getCurrentPrice()));
        eventPublisher.publishEvent(
                new TerritoryHoldStartedEvent(
                        winner.getId(), seasonId, territory.getId(), grade, now));
    }

    private void createAuction(Territory territory, LocalDateTime now) {
        int startingPrice =
                AuctionPolicy.GRADE_START_PRICES.getOrDefault(
                        territory.getGrade().getGrade(), AuctionPolicy.DEFAULT_START_PRICE);

        LocalDateTime endAt = now.plusHours(AuctionPolicy.AUCTION_DURATION_HOURS);
        LocalDateTime maxExtendUntil = endAt.plusMinutes(AuctionPolicy.MAX_EXTEND_UNTIL_MINUTES);

        Auction auction =
                Auction.builder()
                        .territory(territory)
                        .currentPrice(startingPrice)
                        .startAt(now)
                        .endAt(endAt)
                        .maxExtendUntil(maxExtendUntil)
                        .build();
        auctionRepository.save(auction);

        // 시작가 레코드 (bidder = null → 그래프 상 시작점)
        auctionBidRepository.save(
                AuctionBid.builder().auction(auction).bidder(null).price(startingPrice).build());

        territory.startBidding();

        log.info(
                "[AuctionLifecycle] 경매 생성 auctionId={} territoryId={} startingPrice={}",
                auction.getId(),
                territory.getId(),
                startingPrice);
    }
}
