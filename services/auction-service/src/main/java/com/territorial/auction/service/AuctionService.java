package com.territorial.auction.service;

import com.territorial.auction.AuctionPolicy;
import com.territorial.auction.client.BidEscrowRequest;
import com.territorial.auction.client.BidEscrowResult;
import com.territorial.auction.client.WalletClient;
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
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.lock.DistributedLock;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionHistoryRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final WalletClient walletClient;
    private final EventPublisher eventPublisher;

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
                                                a.getTerritoryId(),
                                                a.getCoordX(),
                                                a.getCoordY(),
                                                a.getContinentName(),
                                                a.getGrade(),
                                                a.getCurrentPrice(),
                                                a.getCurrentBidderNickname(),
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
                                                b.getBidderNickname(), b.getPrice(), b.getBidAt()))
                        .toList();
        return new AuctionDetailResponse(
                auction.getId(),
                auction.getTerritoryId(),
                auction.getCoordX(),
                auction.getCoordY(),
                auction.getGrade(),
                auction.getCurrentPrice(),
                auction.getCurrentBidderNickname(),
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
        if (auction.getCurrentBidderNickname() != null
                && auction.getCurrentBidderId().equals(userId)) {
            throw new CustomException(ErrorCode.ALREADY_HIGHEST_BIDDER);
        }
        validateBidAmount(auction.getCurrentPrice(), request.bidAmount());

        Long previousBidderId = auction.getCurrentBidderId();
        Integer previousAmount = previousBidderId != null ? auction.getCurrentPrice() : null;
        BidEscrowResult escrow =
                walletClient.bidEscrow(
                        new BidEscrowRequest(
                                userId, request.bidAmount(), previousBidderId, previousAmount));

        auction.updateBid(userId, escrow.bidderNickname(), request.bidAmount());

        auctionBidRepository.save(
                AuctionBid.builder()
                        .auction(auction)
                        .bidderId(userId)
                        .bidderNickname(escrow.bidderNickname())
                        .price(request.bidAmount())
                        .build());

        applyAntiSniping(auction, now);
        //        [todo] previousBidderId에게 '상회입찰' 알림 -> 이벤트 발행으로
        //        notifyOutbid(previousBidder, auction, request.bidAmount());

        LocalDateTime finalEndAt = auction.getEndAt();
        AuctionBidBroadcast broadcast =
                new AuctionBidBroadcast(
                        auction.getId(),
                        request.bidAmount(),
                        userId,
                        escrow.bidderNickname(),
                        now,
                        finalEndAt);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 실시간 입찰 브로드캐스트(/sub/auction/{id})는 realtime 소비 서비스가 처리 — tracking §1
                        eventPublisher.publish("auction.bid", broadcast);
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
                                            a.getCurrentBidderNickname() != null
                                                    && a.getCurrentBidderId()
                                                            .equals(b.getBidderId());
                                    return new MyBidListResponse.MyBidItemDto(
                                            a.getId(),
                                            a.getTerritoryId(),
                                            a.getCoordX(),
                                            a.getCoordY(),
                                            b.getPrice(),
                                            a.getCurrentPrice(),
                                            isHighest,
                                            a.getEndAt(),
                                            AuctionStatus.from(a.getEndAt(), now),
                                            a.getGrade(),
                                            a.getContinentName());
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
                                                b.getPrice(), b.getBidAt(), b.getBidderNickname()))
                        .toList();
        return new AuctionBidHistoryResponse(auction.getId(), bidDtos);
    }

    public TerritoryAuctionHistoryResponse getTerritoryAuctionHistory(Long territoryId) {
        List<AuctionHistory> histories =
                auctionHistoryRepository.findAllByTerritoryIdOrderByWonAtDesc(territoryId);
        List<TerritoryAuctionHistoryResponse.HistoryDto> historyDtos =
                histories.stream()
                        .map(
                                h ->
                                        new TerritoryAuctionHistoryResponse.HistoryDto(
                                                h.getAuction().getId(),
                                                h.getWinnerName(),
                                                h.getFinalPrice(),
                                                h.getWonAt()))
                        .toList();
        return new TerritoryAuctionHistoryResponse(territoryId, historyDtos);
    }

    private void validateBidAmount(int currentPrice, int bidAmount) {
        int minByPercent = (int) Math.ceil(currentPrice * AuctionPolicy.BID_MIN_PERCENT_RATE);
        int minByFlat = currentPrice + AuctionPolicy.BID_MIN_FLAT_INCREMENT;
        if (bidAmount < Math.max(minByPercent, minByFlat)) {
            throw new CustomException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }
    }

    // 이전 최고 입찰자에게만 입찰 밀림을 알린다. 시작가 레코드(bidder=null)엔 알림 대상이 없다.
    //    private void notifyOutbid(User previousBidder, Auction auction, int newBidAmount) {
    //        if (previousBidder == null) return;
    //        Territory territory = auction.getTerritory();
    //        notificationService.sendNotification(
    //                previousBidder.getId(),
    //                NotificationType.OUTBID,
    //                "("
    //                        + territory.getCoordX()
    //                        + ", "
    //                        + territory.getCoordY()
    //                        + ") 영토 경매에서 입찰이 밀렸습니다. 현재가 "
    //                        + newBidAmount
    //                        + " AP.");
    //    }

    private void applyAntiSniping(Auction auction, LocalDateTime now) {
        LocalDateTime endAt = auction.getEndAt();
        // 종료 AuctionPolicy.ANTI_SNIPE_WINDOW_SECONDS 이내 입찰 시 연장
        // (엔티티 내에서 maxExtendUntil 상한 처리)
        if (!endAt.isAfter(now.plusSeconds(AuctionPolicy.ANTI_SNIPE_WINDOW_SECONDS))) {
            auction.extendEndAt(endAt.plusSeconds(AuctionPolicy.ANTI_SNIPE_EXTEND_SECONDS));
        }
    }
}
