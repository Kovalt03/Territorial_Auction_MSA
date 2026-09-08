package com.territorial.map.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.territorial.map.domain.map.entity.TerritoryAuctionStatus;
import com.territorial.map.domain.map.repository.TerritoryAuctionStatusRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerritoryAuctionStatusServiceTest {

    @Mock private TerritoryAuctionStatusRepository repository;

    @InjectMocks private TerritoryAuctionStatusService service;

    @DisplayName("open — territoryId PK로 프로젝션 행을 저장한다")
    @Test
    void open_savesProjectionRow() {
        LocalDateTime endAt = LocalDateTime.now().plusMinutes(10);

        service.open(1L, 100L, 500, endAt);

        ArgumentCaptor<TerritoryAuctionStatus> captor =
                ArgumentCaptor.forClass(TerritoryAuctionStatus.class);
        verify(repository).save(captor.capture());
        TerritoryAuctionStatus saved = captor.getValue();
        assertThat(saved.getTerritoryId()).isEqualTo(1L);
        assertThat(saved.getAuctionId()).isEqualTo(100L);
        assertThat(saved.getCurrentPrice()).isEqualTo(500);
        assertThat(saved.getEndAt()).isEqualTo(endAt);
    }

    @DisplayName("updateBid — 기존 행이 있으면 현재가·종료시각을 갱신한다")
    @Test
    void updateBid_whenRowExists_updates() {
        LocalDateTime newEndAt = LocalDateTime.now().plusMinutes(30);
        TerritoryAuctionStatus existing =
                TerritoryAuctionStatus.builder()
                        .territoryId(1L)
                        .auctionId(100L)
                        .currentPrice(500)
                        .endAt(LocalDateTime.now().plusMinutes(5))
                        .build();
        when(repository.findByAuctionId(100L)).thenReturn(Optional.of(existing));

        service.updateBid(100L, 800, newEndAt);

        assertThat(existing.getCurrentPrice()).isEqualTo(800);
        assertThat(existing.getEndAt()).isEqualTo(newEndAt);
    }

    @DisplayName("updateBid — open 이벤트가 아직 없으면(순서 역전) 아무 것도 하지 않고 자가 치유에 맡긴다")
    @Test
    void updateBid_whenRowMissing_noOp() {
        when(repository.findByAuctionId(999L)).thenReturn(Optional.empty());

        service.updateBid(999L, 800, LocalDateTime.now().plusMinutes(30));

        verify(repository).findByAuctionId(999L);
        verify(repository, never()).save(any());
    }

    @DisplayName("close — 해당 경매 행을 auctionId로 삭제한다")
    @Test
    void close_deletesByAuctionId() {
        service.close(100L);

        verify(repository).deleteByAuctionId(100L);
    }
}
