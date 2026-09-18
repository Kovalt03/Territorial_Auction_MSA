package com.territorial.map.domain.map.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.common.ApiResponse;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.map.client.CombatResourceClient;
import com.territorial.map.client.CombatResourceClient.TerritoryStorageView;
import com.territorial.map.client.NicknameClient;
import com.territorial.map.domain.map.dto.GridMapResponse;
import com.territorial.map.domain.map.dto.TerritoryDetailResponse;
import com.territorial.map.domain.map.entity.ColorHistory;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.entity.Territory.TerritoryStatus;
import com.territorial.map.domain.map.entity.TerritoryAuctionStatus;
import com.territorial.map.domain.map.repository.ColorHistoryRepository;
import com.territorial.map.domain.map.repository.TerritoryAuctionStatusRepository;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import com.territorial.map.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

    private static final int COLOR_CHANGE_LIMIT = 3;

    private final TerritoryRepository territoryRepository;
    private final TerritoryAuctionStatusRepository territoryAuctionStatusRepository;
    private final CombatResourceClient combatResourceClient;
    private final ColorHistoryRepository colorHistoryRepository;
    private final TerritoryIncomeService territoryIncomeService;
    private final NicknameClient nicknameClient;
    private final ObjectMapper objectMapper;

    // 직렬화된 그리드 JSON 로컬(in-JVM) 캐시 — 요청당 2500영토 재직렬화 + Redis 대용량 blob 왕복을 제거(핫패스).
    // etag가 바뀌면(맵 변경) 캐시를 통째로 교체, 그 외 변경은 TTL로 반영 → territory-grid(1분)와 신선도 동일.
    private static final long GRID_JSON_TTL_MILLIS = 60_000;
    private final AtomicReference<GridJsonSnapshot> gridJsonCache = new AtomicReference<>();

    private record GridJsonSnapshot(
            String eTag, long expiresAtMillis, ConcurrentHashMap<String, String> byContinent) {}

    public String getGridMapJson(Long continentId, String eTag) {
        String key = (continentId == null) ? "all" : continentId.toString();
        GridJsonSnapshot snapshot = gridJsonCache.get();
        long now = System.currentTimeMillis();
        if (snapshot == null || !snapshot.eTag().equals(eTag) || now > snapshot.expiresAtMillis()) {
            snapshot =
                    new GridJsonSnapshot(
                            eTag, now + GRID_JSON_TTL_MILLIS, new ConcurrentHashMap<>());
            gridJsonCache.set(snapshot);
        }
        return snapshot.byContinent().computeIfAbsent(key, k -> serializeGrid(continentId));
    }

    private String serializeGrid(Long continentId) {
        try {
            return objectMapper.writeValueAsString(ApiResponse.ok(getGridMap(continentId)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("grid map JSON 직렬화 실패", e);
        }
    }

    @Cacheable(value = "territory-grid", key = "#continentId ?: 'all'", sync = true)
    public GridMapResponse getGridMap(Long continentId) {
        List<Territory> territories =
                (continentId == null)
                        ? territoryRepository.findAllWithContinentAndGrade()
                        : territoryRepository.findAllByContinentId(continentId);

        List<Long> territoryIds = territories.stream().map(Territory::getId).toList();
        Set<Long> activeAuctionTerritoryIds =
                territoryAuctionStatusRepository
                        .findByTerritoryIdInAndEndAtAfter(territoryIds, LocalDateTime.now())
                        .stream()
                        .map(TerritoryAuctionStatus::getTerritoryId)
                        .collect(Collectors.toSet());

        List<Long> ownerIds =
                territories.stream()
                        .map(Territory::getOwnerId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, String> nicknames = nicknameClient.getNicknames(ownerIds);

        List<GridMapResponse.GridTerritoryDto> gridMapDtos =
                territories.stream()
                        .map(
                                t ->
                                        new GridMapResponse.GridTerritoryDto(
                                                t.getId(),
                                                t.getCoordX(),
                                                t.getCoordY(),
                                                t.getOwnerId(),
                                                t.getOwnerId() != null
                                                        ? nicknames.get(t.getOwnerId())
                                                        : null,
                                                t.getCurrentColor(),
                                                t.getGrade().getGrade(),
                                                t.getStatus().name(),
                                                activeAuctionTerritoryIds.contains(t.getId()),
                                                t.getContinent().getId(),
                                                t.getGrade().getGridSize()))
                        .toList();
        return new GridMapResponse(50, gridMapDtos);
    }

    public TerritoryDetailResponse getTerritoryDetail(Long territoryId) {
        Territory territory =
                territoryRepository
                        .findByIdWithDetails(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));

        TerritoryStorageView combat = combatResourceClient.getTerritoryStorage(territoryId);
        List<TerritoryDetailResponse.BuildingInfo> buildingInfos =
                combat.buildings().stream()
                        .map(
                                b ->
                                        new TerritoryDetailResponse.BuildingInfo(
                                                b.buildingId(),
                                                b.name(),
                                                b.level(),
                                                b.hp(),
                                                b.maxHp()))
                        .toList();

        TerritoryDetailResponse.OwnerInfo owner =
                (territory.getOwnerId() == null)
                        ? null
                        : new TerritoryDetailResponse.OwnerInfo(
                                territory.getOwnerId(),
                                nicknameClient.getNickname(territory.getOwnerId()),
                                territory.getCurrentColor());

        TerritoryDetailResponse.AuctionInfo auction =
                territoryAuctionStatusRepository
                        .findByTerritoryIdAndEndAtAfter(territoryId, LocalDateTime.now())
                        .map(
                                s ->
                                        new TerritoryDetailResponse.AuctionInfo(
                                                s.getAuctionId(),
                                                s.getCurrentPrice(),
                                                s.getEndAt()))
                        .orElse(null);

        // 성·저장소가 함께 GP 를 담는다. 점유 중이면 성이 있어 목록이 비지 않는다.
        boolean hasStorage =
                territory.getStatus() == TerritoryStatus.OCCUPIED && combat.storageCapacity() > 0;

        Integer productionRatePerMin =
                hasStorage ? territoryIncomeService.calculateEffectiveRate(territory) : null;
        LocalDateTime lastProducedAt = hasStorage ? territory.getLastProducedAt() : null;
        Integer storedGp = hasStorage ? combat.storedGp() : null;
        Integer storageCapacity = hasStorage ? combat.storageCapacity() : null;

        return new TerritoryDetailResponse(
                territory.getId(),
                territory.getCoordX(),
                territory.getCoordY(),
                territory.getContinent().getDisplayName(),
                territory.getGrade().getGrade(),
                territory.getGrade().getProductionMultiplier(),
                territory.getGrade().getGridSize(),
                territory.getGrade().getZone1Radius(),
                territory.getGrade().getZone2Radius(),
                territory.getStatus().name(),
                owner,
                territory.getBaseProductionRate(),
                false, // TODO: Redis invincible:{territoryId} 키 존재 여부로 교체
                buildingInfos,
                auction,
                productionRatePerMin,
                lastProducedAt,
                storedGp,
                storageCapacity);
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void changeColor(Long territoryId, Long userId, String colorCode) {
        Territory territory =
                territoryRepository
                        .findByIdWithDetails(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));

        validateOwner(territory, userId);
        validateOccupied(territory);
        validateColorChangeCount(territoryId, userId);

        territory.updateColor(colorCode);

        colorHistoryRepository.save(
                ColorHistory.builder()
                        .territory(territory)
                        .userId(territory.getOwnerId())
                        .colorCode(colorCode)
                        .build());
    }

    private void validateOwner(Territory territory, Long userId) {
        if (territory.getOwnerId() == null || !territory.getOwnerId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_TERRITORY_OWNER);
        }
    }

    private void validateOccupied(Territory territory) {
        if (territory.getStatus() != TerritoryStatus.OCCUPIED
                || territory.getOccupiedUntil() == null
                || territory.getOccupiedUntil().isBefore(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }
    }

    private void validateColorChangeCount(Long territoryId, Long userId) {
        long changeCount = colorHistoryRepository.countByTerritoryIdAndUserId(territoryId, userId);
        if (changeCount >= COLOR_CHANGE_LIMIT) {
            throw new CustomException(ErrorCode.COLOR_CHANGE_LIMIT_EXCEEDED);
        }
    }
}
