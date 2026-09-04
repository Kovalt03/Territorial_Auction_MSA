package com.territorial.map.domain.map.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.map.client.NicknameClient;
import com.territorial.map.domain.map.TerritoryPolicy;
import com.territorial.map.domain.map.dto.MapUpdateBroadcast;
import com.territorial.map.domain.map.dto.TerritoryCombatContextResponse;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import com.territorial.map.global.exception.ErrorCode;
import com.territorial.map.internal.dto.OwnerHoldingPage;
import com.territorial.map.internal.dto.OwnerHoldingView;
import com.territorial.map.internal.dto.OwnerTerritoryCount;
import com.territorial.map.realtime.MapRealtimePublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerritoryService {

    private final TerritoryRepository territoryRepository;
    private final NicknameClient nicknameClient;
    private final MapRealtimePublisher mapRealtimePublisher;

    public TerritoryCombatContextResponse getCombatContext(Long territoryId) {
        return TerritoryCombatContextResponse.from(
                territoryRepository
                        .findByIdWithDetails(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND)));
    }

    public List<TerritoryCombatContextResponse> getOwnedCombatContexts(Long userId) {
        return territoryRepository
                .findAllOccupiedByOwnerId(userId, Territory.TerritoryStatus.OCCUPIED)
                .stream()
                .map(TerritoryCombatContextResponse::from)
                .toList();
    }

    public boolean exists(Long territoryId) {
        return territoryRepository.existsById(territoryId);
    }

    public long getOwnerCount(Long userId) {
        return territoryRepository.countByOwnerId(userId);
    }

    // 멤버 표시 통계용 — OCCUPIED 영토만 소유자별로 집계한다(모놀리식 MemberStats 계약과 동일).
    public List<OwnerTerritoryCount> getOwnerCounts(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return territoryRepository
                .countGroupByOwnerIds(userIds, Territory.TerritoryStatus.OCCUPIED)
                .stream()
                .map(row -> new OwnerTerritoryCount((Long) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    public OwnerHoldingPage getOwnerHoldings(Long userId, int page, int size) {
        Page<Territory> territoryPage =
                territoryRepository.findAllByUserId(userId, PageRequest.of(page, size));
        List<OwnerHoldingView> content =
                territoryPage.getContent().stream().map(OwnerHoldingView::from).toList();
        return new OwnerHoldingPage(content, territoryPage.getTotalElements());
    }

    public List<Long> getOwnerTerritoryIds(Long userId) {
        return territoryRepository.findByOwnerId(userId).stream().map(Territory::getId).toList();
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void takeOverFromSiege(Long territoryId, Long newOwnerId, Long formerOwnerId) {
        Territory territory =
                territoryRepository
                        .findByIdWithDetails(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        Long currentOwnerId = territory.getOwnerId();
        if (Objects.equals(currentOwnerId, newOwnerId)) {
            return;
        }
        if (!Objects.equals(currentOwnerId, formerOwnerId)) {
            log.warn(
                    "오래된 공성 인계 요청 무시. territoryId={}, currentOwnerId={}, formerOwnerId={}",
                    territoryId,
                    currentOwnerId,
                    formerOwnerId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        territory.occupy(
                newOwnerId,
                now.plusDays(TerritoryPolicy.OCCUPATION_DURATION_DAYS),
                now.plusHours(TerritoryPolicy.PROTECTION_DURATION_HOURS));
        String newOwnerNickname = nicknameClient.getNickname(newOwnerId);
        sendMapUpdateAfterCommit(territory, newOwnerId, newOwnerNickname);
    }

    private void sendMapUpdateAfterCommit(
            Territory territory, Long newOwnerId, String newOwnerNickname) {
        MapUpdateBroadcast update =
                new MapUpdateBroadcast(
                        territory.getId(),
                        territory.getCoordX(),
                        territory.getCoordY(),
                        newOwnerId,
                        newOwnerNickname,
                        "OCCUPIED");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mapRealtimePublisher.publish(update);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        mapRealtimePublisher.publish(update);
                    }
                });
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void occupy(
            Long territoryId,
            Long winnerId,
            LocalDateTime occupiedUntil,
            LocalDateTime protectedUntil) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        territory.occupy(winnerId, occupiedUntil, protectedUntil);
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void release(Long territoryId, LocalDateTime nextAuctionAt) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        territory.release(nextAuctionAt);
    }
}
