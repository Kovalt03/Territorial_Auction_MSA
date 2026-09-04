package com.territorial.map.domain.map.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.map.client.CombatResourceClient;
import com.territorial.map.client.CombatResourceClient.CreditIncomeResponse;
import com.territorial.map.client.NotificationClient;
import com.territorial.map.domain.map.TerritoryIncomePolicy;
import com.territorial.map.domain.map.dto.CollectTerritoryResponse;
import com.territorial.map.domain.map.entity.BonusTile;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.entity.TerritoryProductionLog;
import com.territorial.map.domain.map.entity.TerritoryProductionLog.ProductionReason;
import com.territorial.map.domain.map.repository.BonusTileRepository;
import com.territorial.map.domain.map.repository.TerritoryProductionLogRepository;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import com.territorial.map.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerritoryIncomeService {

    private final TerritoryRepository territoryRepository;
    private final CombatResourceClient combatResourceClient;
    private final BonusTileRepository bonusTileRepository;
    private final TerritoryProductionLogRepository productionLogRepository;
    private final NotificationClient notificationClient;

    private record RateBreakdown(int baseRate, int bonusTileRate, int adjacentRate) {
        int total() {
            return baseRate + bonusTileRate + adjacentRate;
        }
    }

    private record GpBreakdown(int credited, int baseGp, int bonusTileGp, int adjacentGp) {}

    @Transactional
    public CollectTerritoryResponse collect(Long userId, Long territoryId) {
        Territory territory =
                territoryRepository
                        .findByIdWithDetails(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        validateOwner(territory, userId);
        validateOccupied(territory);

        return settle(territory);
    }

    public int calculateEffectiveRate(Territory territory) {
        if (territory.getOwnerId() == null) return 0;
        return computeRateBreakdown(territory).total();
    }

    private CollectTerritoryResponse settle(Territory territory) {
        LocalDateTime now = LocalDateTime.now();
        if (territory.getLastProducedAt() == null) {
            territory.updateLastProducedAt(now);
            return buildCollectResponse(territory, currentStorage(territory.getId()), 0);
        }
        long elapsedMinutes = ChronoUnit.MINUTES.between(territory.getLastProducedAt(), now);
        if (elapsedMinutes < 1) {
            return buildCollectResponse(territory, currentStorage(territory.getId()), 0);
        }

        RateBreakdown rates = computeRateBreakdown(territory);
        long raw = (long) rates.total() * elapsedMinutes;
        int requestedGp = (int) Math.min(raw, Integer.MAX_VALUE);
        String commandKey =
                "TERRITORY_INCOME:" + territory.getId() + ":" + territory.getLastProducedAt();
        CreditIncomeResponse credited =
                combatResourceClient.creditIncome(territory.getId(), requestedGp, commandKey);
        GpBreakdown gp = allocateGpBreakdown(rates, elapsedMinutes, credited.creditedGp());
        if (gp.credited() > 0) {
            saveProductionLogs(territory, territory.getOwnerId(), gp);
            notifyStorageFull(territory, credited);
            log.info(
                    "영토 수입 정산. territoryId={}, ownerId={}, creditedGp={}, elapsedMinutes={}",
                    territory.getId(),
                    territory.getOwnerId(),
                    gp.credited(),
                    elapsedMinutes);
        }
        territory.updateLastProducedAt(now);
        return buildCollectResponse(territory, credited, gp.credited());
    }

    private GpBreakdown allocateGpBreakdown(RateBreakdown rates, long elapsed, int credited) {
        long totalRaw = (long) rates.total() * elapsed;
        if (credited == 0 || totalRaw == 0) return new GpBreakdown(0, 0, 0, 0);

        double scale = (double) credited / totalRaw;
        int baseGp = (int) Math.floor((long) rates.baseRate() * elapsed * scale);
        int bonusTileGp = (int) Math.floor((long) rates.bonusTileRate() * elapsed * scale);
        return new GpBreakdown(credited, baseGp, bonusTileGp, credited - baseGp - bonusTileGp);
    }

    private RateBreakdown computeRateBreakdown(Territory territory) {
        double base = territory.getBaseProductionRate();
        double grade = territory.getGrade().getProductionMultiplier().doubleValue();
        BonusTile bonusTile = bonusTileRepository.findByTerritoryId(territory.getId()).orElse(null);
        double btMul = bonusTile != null ? bonusTile.getMultiplier().doubleValue() : 1.0;
        int adjacentCount =
                territoryRepository.countAdjacentOccupiedByOwner(
                        territory.getCoordX(),
                        territory.getCoordY(),
                        territory.getOwnerId(),
                        territory.getId(),
                        Territory.TerritoryStatus.OCCUPIED);

        int baseRate = (int) Math.floor(base * grade);
        int rateWithBt = (int) Math.floor(base * grade * btMul);
        int totalRate =
                (int)
                        Math.floor(
                                base
                                        * grade
                                        * btMul
                                        * (1.0
                                                + adjacentCount
                                                        * TerritoryIncomePolicy
                                                                .ADJACENT_BONUS_RATE));
        return new RateBreakdown(baseRate, rateWithBt - baseRate, totalRate - rateWithBt);
    }

    private void saveProductionLogs(Territory territory, Long ownerId, GpBreakdown gp) {
        if (gp.baseGp() > 0) saveLog(territory, ownerId, gp.baseGp(), ProductionReason.BASE);
        if (gp.bonusTileGp() > 0)
            saveLog(territory, ownerId, gp.bonusTileGp(), ProductionReason.BONUS_TILE);
        if (gp.adjacentGp() > 0)
            saveLog(territory, ownerId, gp.adjacentGp(), ProductionReason.ADJACENT_BONUS);
    }

    // 수입 적립으로 저장 공간이 막 가득 찼을 때만 알린다. 이미 가득 찬 상태면 적립분이 0이라 재발송되지 않는다.
    private void notifyStorageFull(Territory territory, CreditIncomeResponse storage) {
        if (storage.storedGp() < storage.storageCapacity()) return;
        notificationClient.sendNotification(
                territory.getOwnerId(),
                "INCOME",
                "("
                        + territory.getCoordX()
                        + ", "
                        + territory.getCoordY()
                        + ") 영토 저장소가 가득 찼습니다. GP를 수거하지 않으면 추가 수입이 소멸됩니다.");
    }

    private void saveLog(Territory territory, Long ownerId, int amount, ProductionReason reason) {
        productionLogRepository.save(
                TerritoryProductionLog.builder()
                        .territory(territory)
                        .ownerId(ownerId)
                        .amount(amount)
                        .reason(reason)
                        .build());
    }

    private CreditIncomeResponse currentStorage(Long territoryId) {
        var storage = combatResourceClient.getTerritoryStorage(territoryId);
        return new CreditIncomeResponse(0, storage.storedGp(), storage.storageCapacity());
    }

    private CollectTerritoryResponse buildCollectResponse(
            Territory territory, CreditIncomeResponse storage, int creditedGp) {
        return new CollectTerritoryResponse(
                creditedGp,
                storage.storedGp(),
                calculateEffectiveRate(territory),
                territory.getLastProducedAt(),
                storage.storageCapacity());
    }

    private void validateOwner(Territory territory, Long userId) {
        if (territory.getOwnerId() == null || !territory.getOwnerId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_TERRITORY_OWNER);
        }
    }

    private void validateOccupied(Territory territory) {
        if (territory.getStatus() != Territory.TerritoryStatus.OCCUPIED
                || territory.getOccupiedUntil() == null
                || territory.getOccupiedUntil().isBefore(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }
    }
}
