package com.territorial.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.entity.Auction;
import com.territorial.auction.entity.AuctionBid;
import com.territorial.auction.event.EventPublisher;
import com.territorial.auction.event.TerritoryAuctionReadyEvent;
import com.territorial.auction.repository.AuctionBidRepository;
import com.territorial.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuctionCreationServiceTest {

    @InjectMocks private AuctionCreationService creationService;

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionBidRepository auctionBidRepository;
    @Mock private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private TerritoryAuctionReadyEvent event(String grade) {
        return new TerritoryAuctionReadyEvent(1L, 5, 7, "북부", 1L, grade);
    }

    @Test
    @DisplayName("A등급 → 시작가 5000으로 경매·시작 입찰 저장")
    void createAuction_gradeA() {
        given(auctionRepository.findFirstByTerritoryIdAndSettledFalseOrderByEndAtDesc(1L))
                .willReturn(Optional.empty());

        creationService.createAuction(event("A"));

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice()).isEqualTo(5000);
        verify(auctionBidRepository).save(any(AuctionBid.class));
    }

    @Test
    @DisplayName("미등록 등급 → 기본 시작가 1000")
    void createAuction_unknownGrade() {
        given(auctionRepository.findFirstByTerritoryIdAndSettledFalseOrderByEndAtDesc(1L))
                .willReturn(Optional.empty());

        creationService.createAuction(event("Z"));

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice()).isEqualTo(1000);
    }

    @Test
    @DisplayName("종료 시각 = 생성 시각 + 24시간")
    void createAuction_endAt24h() {
        given(auctionRepository.findFirstByTerritoryIdAndSettledFalseOrderByEndAtDesc(1L))
                .willReturn(Optional.empty());
        LocalDateTime before = LocalDateTime.now();

        creationService.createAuction(event("A"));

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());
        LocalDateTime endAt = captor.getValue().getEndAt();
        assertThat(endAt).isAfter(before.plusHours(23).plusMinutes(59));
        assertThat(endAt).isBefore(before.plusHours(24).plusMinutes(1));
    }

    @Test
    @DisplayName("진행 중 경매가 이미 있으면 스킵(idempotent)")
    void createAuction_idempotent() {
        given(auctionRepository.findFirstByTerritoryIdAndSettledFalseOrderByEndAtDesc(1L))
                .willReturn(Optional.of(Auction.builder().territoryId(1L).build()));

        creationService.createAuction(event("A"));

        verify(auctionRepository, never()).save(any());
        verify(auctionBidRepository, never()).save(any());
    }
}
