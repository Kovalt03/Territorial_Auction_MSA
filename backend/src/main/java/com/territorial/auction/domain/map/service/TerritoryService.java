package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.TerritoryPolicy;
import com.territorial.auction.domain.map.dto.MapUpdateBroadcast;
import com.territorial.auction.domain.map.dto.TerritoryCombatContextResponse;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

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

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void takeOverFromSiege(Long territoryId, Long newOwnerId, Long formerOwnerId) {
        Territory territory =
                territoryRepository
                        .findByIdWithDetails(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        Long currentOwnerId = territory.getOwner() != null ? territory.getOwner().getId() : null;
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
        User newOwner =
                userRepository
                        .findById(newOwnerId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        territory.occupy(
                newOwner,
                now.plusDays(TerritoryPolicy.OCCUPATION_DURATION_DAYS),
                now.plusHours(TerritoryPolicy.PROTECTION_DURATION_HOURS));
        sendMapUpdateAfterCommit(territory, newOwner);
    }

    private void sendMapUpdateAfterCommit(Territory territory, User newOwner) {
        MapUpdateBroadcast update =
                new MapUpdateBroadcast(
                        territory.getId(),
                        territory.getCoordX(),
                        territory.getCoordY(),
                        newOwner.getId(),
                        newOwner.getNickname(),
                        "OCCUPIED");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messagingTemplate.convertAndSend("/sub/map/update", update);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend("/sub/map/update", update);
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
        // FK만 세팅 — getReferenceById는 프록시라 실제 조회가 없다(occupy가 winner 속성을 읽지 않음).
        // winnerId는 낙찰자라 항상 유효. map 분리 시 Territory.owner→ownerId로 바뀌면 이 로드도 사라짐.
        User winner = userRepository.getReferenceById(winnerId);
        territory.occupy(winner, occupiedUntil, protectedUntil);
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
