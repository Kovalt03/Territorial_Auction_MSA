package com.territorial.auction.service;

import com.territorial.auction.AuctionPolicy;
import com.territorial.auction.dto.AuctionBidBroadcast;
import com.territorial.auction.dto.AuctionBidHistoryResponse;
import com.territorial.auction.dto.AuctionDetailResponse;
import com.territorial.auction.dto.AuctionListResponse;
import com.territorial.auction.dto.MyBidListResponse;
import com.territorial.auction.dto.PlaceBidRequest;
import com.territorial.auction.dto.PlaceBidResponse;
import com.territorial.auction.dto.TerritoryAuctionHistoryResponse;
import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionBid;
import com.territorial.auction.entity.AuctionHistory;
import com.territorial.auction.entity.AuctionStatus;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.lock.DistributedLock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionHistoryRepository auctionHistoryRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TerritoryRepository territoryRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public Auction findById(Long auctionId) {
        return auctionRepository
                .findById(auctionId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
    }

    public AuctionListResponse getAuctions(
            Long continentId, AuctionStatus status, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        String statusName = status != null ? status.name() : null;
        Page<Auction> page =
                auctionRepository.findAllWithFilter(continentId, statusName, now, pageable);
        List<AuctionListResponse.AuctionItemDto> items =
                page.getContent().stream()
                        .map(
                                a ->
                                        new AuctionListResponse.AuctionItemDto(
                                                a.getId(),
                                                a.getTerritory().getId(),
                                                a.getTerritory().getCoordX(),
                                                a.getTerritory().getCoordY(),
                                                a.getTerritory().getContinent().getDisplayName(),
                                                a.getTerritory().getGrade().getGrade(),
                                                a.getCurrentPrice(),
                                                a.getCurrentBidder() != null
                                                        ? a.getCurrentBidder().getNickname()
                                                        : null,
                                                a.getEndAt(),
                                                AuctionStatus.from(a.getEndAt(), now)))
                        .toList();

        return new AuctionListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), items);
    }

    public AuctionDetailResponse getAuctionDetail(Long auctionId) {
        Auction auction =
                auctionRepository
                        .findById(auctionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
        List<AuctionBid> recentBids =
                auctionBidRepository.findTop5ByAuctionIdOrderByBidAtDesc(auctionId);
        List<AuctionDetailResponse.RecentBidDto> recentBidDtos =
                recentBids.stream()
                        .map(
                                b ->
                                        new AuctionDetailResponse.RecentBidDto(
                                                b.getBidder() != null
                                                        ? b.getBidder().getNickname()
                                                        : null,
                                                b.getPrice(),
                                                b.getBidAt()))
                        .toList();
        return new AuctionDetailResponse(
                auction.getId(),
                auction.getTerritory().getId(),
                auction.getTerritory().getCoordX(),
                auction.getTerritory().getCoordY(),
                auction.getTerritory().getGrade().getGrade(),
                auction.getCurrentPrice(),
                auction.getCurrentBidder() != null
                        ? auction.getCurrentBidder().getNickname()
                        : null,
                auction.getStartAt(),
                auction.getEndAt(),
                recentBidDtos);
    }

    @Transactional
    @DistributedLock(key = "'lock:auction:' + #auctionId")
    public PlaceBidResponse placeBid(Long userId, Long auctionId, PlaceBidRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Auction auction =
                auctionRepository
                        .findById(auctionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));

        if (now.isAfter(auction.getEndAt())) {
            throw new CustomException(ErrorCode.AUCTION_ALREADY_ENDED);
        }
        if (auction.getCurrentBidder() != null
                && auction.getCurrentBidder().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ALREADY_HIGHEST_BIDDER);
        }
        validateBidAmount(auction.getCurrentPrice(), request.bidAmount());

        User bidder =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User previousBidder = auction.getCurrentBidder();
        LockedWallets wallets =
                lockWallets(userId, previousBidder != null ? previousBidder.getId() : null);
        Wallet bidderWallet = wallets.bidder();

        if (bidderWallet.getAvailableAp() < request.bidAmount()) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }

        refundPreviousBidder(auction, auctionId, wallets.previousBidder());
        bidderWallet.lockAp(request.bidAmount());
        auction.updateBid(bidder, request.bidAmount());

        auctionBidRepository.save(
                AuctionBid.builder()
                        .auction(auction)
                        .bidder(bidder)
                        .price(request.bidAmount())
                        .build());

        applyAntiSniping(auction, now);
        notifyOutbid(previousBidder, auction, request.bidAmount());

        LocalDateTime finalEndAt = auction.getEndAt();
        AuctionBidBroadcast broadcast =
                new AuctionBidBroadcast(
                        auction.getId(),
                        request.bidAmount(),
                        bidder.getId(),
                        bidder.getNickname(),
                        now,
                        finalEndAt);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/sub/auction/" + auction.getId(), broadcast);
                    }
                });

        return new PlaceBidResponse(auction.getId(), request.bidAmount(), finalEndAt);
    }

    public MyBidListResponse getMyBids(Long userId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        List<AuctionBid> bidsRaw = auctionBidRepository.findLatestBidPerAuctionByBidder(userId);
        List<MyBidListResponse.MyBidItemDto> bids =
                bidsRaw.stream()
                        .map(
                                b -> {
                                    Auction a = b.getAuction();
                                    boolean isHighest =
                                            a.getCurrentBidder() != null
                                                    && a.getCurrentBidder()
                                                            .getId()
                                                            .equals(b.getBidder().getId());
                                    return new MyBidListResponse.MyBidItemDto(
                                            a.getId(),
                                            a.getTerritory().getId(),
                                            a.getTerritory().getCoordX(),
                                            a.getTerritory().getCoordY(),
                                            b.getPrice(),
                                            a.getCurrentPrice(),
                                            isHighest,
                                            a.getEndAt(),
                                            AuctionStatus.from(a.getEndAt(), now),
                                            a.getTerritory().getGrade().getGrade(),
                                            a.getTerritory().getContinent().getDisplayName());
                                })
                        .toList();
        return new MyBidListResponse(bids.size(), 0, bids.size(), bids);
    }

    public AuctionBidHistoryResponse getAuctionBidHistory(Long auctionId) {
        Auction auction =
                auctionRepository
                        .findById(auctionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
        List<AuctionBid> bids = auctionBidRepository.findAllByAuctionIdOrderByBidAtAsc(auctionId);
        List<AuctionBidHistoryResponse.BidDto> bidDtos =
                bids.stream()
                        .map(
                                b ->
                                        new AuctionBidHistoryResponse.BidDto(
                                                b.getPrice(),
                                                b.getBidAt(),
                                                b.getBidder() != null
                                                        ? b.getBidder().getNickname()
                                                        : null))
                        .toList();
        return new AuctionBidHistoryResponse(auction.getId(), bidDtos);
    }

    public TerritoryAuctionHistoryResponse getTerritoryAuctionHistory(Long territoryId) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        List<AuctionHistory> histories =
                auctionHistoryRepository.findAllByTerritoryIdOrderByWonAtDesc(territoryId);
        List<TerritoryAuctionHistoryResponse.HistoryDto> historyDtos =
                histories.stream()
                        .map(
                                h ->
                                        new TerritoryAuctionHistoryResponse.HistoryDto(
                                                h.getAuction().getId(),
                                                h.getWinner().getNickname(),
                                                h.getFinalPrice(),
                                                h.getWonAt()))
                        .toList();
        return new TerritoryAuctionHistoryResponse(territory.getId(), historyDtos);
    }

    private void validateBidAmount(int currentPrice, int bidAmount) {
        int minByPercent = (int) Math.ceil(currentPrice * AuctionPolicy.BID_MIN_PERCENT_RATE);
        int minByFlat = currentPrice + AuctionPolicy.BID_MIN_FLAT_INCREMENT;
        if (bidAmount < Math.max(minByPercent, minByFlat)) {
            throw new CustomException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }
    }

    // 이전 최고 입찰자에게만 입찰 밀림을 알린다. 시작가 레코드(bidder=null)엔 알림 대상이 없다.
    private void notifyOutbid(User previousBidder, Auction auction, int newBidAmount) {
        if (previousBidder == null) return;
        Territory territory = auction.getTerritory();
        notificationService.sendNotification(
                previousBidder.getId(),
                NotificationType.OUTBID,
                "("
                        + territory.getCoordX()
                        + ", "
                        + territory.getCoordY()
                        + ") 영토 경매에서 입찰이 밀렸습니다. 현재가 "
                        + newBidAmount
                        + " AP.");
    }

    private LockedWallets lockWallets(Long bidderId, Long previousBidderId) {
        if (previousBidderId == null) {
            return new LockedWallets(findWalletWithLock(bidderId), null);
        }
        if (previousBidderId < bidderId) {
            Wallet previousWallet = findWalletWithLock(previousBidderId);
            return new LockedWallets(findWalletWithLock(bidderId), previousWallet);
        }
        Wallet bidderWallet = findWalletWithLock(bidderId);
        return new LockedWallets(bidderWallet, findWalletWithLock(previousBidderId));
    }

    private Wallet findWalletWithLock(Long userId) {
        return walletRepository
                .findByIdWithLock(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void refundPreviousBidder(
            Auction auction, Long auctionId, Wallet previousBidderWallet) {
        User prevBidder = auction.getCurrentBidder();
        if (prevBidder == null) return;
        auctionBidRepository
                .findTopByAuctionIdAndBidderIdOrderByPriceDesc(auctionId, prevBidder.getId())
                .ifPresent(prevBid -> previousBidderWallet.refundLockedAp(prevBid.getPrice()));
    }

    private record LockedWallets(Wallet bidder, Wallet previousBidder) {}

    private void applyAntiSniping(Auction auction, LocalDateTime now) {
        LocalDateTime endAt = auction.getEndAt();
        // 종료 AuctionPolicy.ANTI_SNIPE_WINDOW_SECONDS 이내 입찰 시 연장
        // (엔티티 내에서 maxExtendUntil 상한 처리)
        if (!endAt.isAfter(now.plusSeconds(AuctionPolicy.ANTI_SNIPE_WINDOW_SECONDS))) {
            auction.extendEndAt(endAt.plusSeconds(AuctionPolicy.ANTI_SNIPE_EXTEND_SECONDS));
        }
    }
}
