package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.combat.client.CombatResourceClient;
import com.territorial.auction.domain.map.LandTaxPolicy;
import com.territorial.auction.domain.map.TerritoryPolicy;
import com.territorial.auction.domain.map.dto.TaxLogResponse;
import com.territorial.auction.domain.map.dto.TaxStatusResponse;
import com.territorial.auction.domain.map.entity.LandTaxLog;
import com.territorial.auction.domain.map.entity.LandTaxLog.TaxStatus;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.event.TerritoryLostEvent;
import com.territorial.auction.domain.map.repository.LandTaxLogRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.notification.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.SeasonQueryClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LandTaxService {

    private static final String CACHE_KEY_PREFIX = "land_tax:expected:";
    private static final String GRACE_KEY_PREFIX = "land_tax:grace:";
    private static final Map<String, Integer> GRADE_EVICTION_ORDER =
            Map.of("D", 1, "C", 2, "B", 3, "A", 4, "S", 5);

    private final TerritoryRepository territoryRepository;
    private final LandTaxLogRepository landTaxLogRepository;
    private final SeasonQueryClient seasonQueryClient;
    private final CombatResourceClient combatResourceClient;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public TaxStatusResponse getLandTaxStatus(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof TaxStatusResponse response) {
                return response;
            }
        } catch (Exception e) {
            log.warn("토지세 현황 Redis 캐시 조회 실패. userId={}", userId, e);
        }

        TaxStatusResponse response = computeTaxStatus(userId);

        try {
            LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
            Duration ttl = Duration.between(LocalDateTime.now(), midnight);
            redisTemplate.opsForValue().set(cacheKey, response, ttl);
        } catch (Exception e) {
            log.warn("토지세 현황 Redis 캐시 저장 실패. userId={}", userId, e);
        }

        return response;
    }

    private TaxStatusResponse computeTaxStatus(Long userId) {
        int territoryCount = (int) territoryRepository.countByOwnerId(userId);

        int seasonPassExemptBonus = seasonQueryClient.getTaxExemptBonus(userId);

        int baseExemptedCount = Math.min(territoryCount, LandTaxPolicy.BASE_EXEMPT_COUNT);
        int baseTaxableCount = Math.max(0, territoryCount - LandTaxPolicy.BASE_EXEMPT_COUNT);
        int baseDailyTax = LandTaxPolicy.calculateDailyTax(baseTaxableCount);

        int effectiveExemptCount = LandTaxPolicy.BASE_EXEMPT_COUNT + seasonPassExemptBonus;
        int finalTaxableCount = Math.max(0, territoryCount - effectiveExemptCount);
        int finalDailyGP = LandTaxPolicy.calculateDailyTax(finalTaxableCount);

        return new TaxStatusResponse(
                territoryCount,
                new TaxStatusResponse.TaxBreakdown(
                        baseExemptedCount, baseTaxableCount, baseDailyTax),
                seasonPassExemptBonus,
                effectiveExemptCount,
                finalDailyGP,
                LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).plusDays(1));
    }

    public TaxLogResponse getLandTaxLogs(Long userId, TaxStatus status, Pageable pageable) {
        Page<LandTaxLog> taxLogs =
                (status == null)
                        ? landTaxLogRepository.findByUserIdOrderByChargedAtDesc(userId, pageable)
                        : landTaxLogRepository.findByUserIdAndStatusOrderByChargedAtDesc(
                                userId, status, pageable);

        List<TaxLogResponse.TaxLogItem> logs =
                taxLogs.getContent().stream()
                        .map(
                                log ->
                                        new TaxLogResponse.TaxLogItem(
                                                log.getId(),
                                                log.getChargedAt(),
                                                log.getTerritoryCount(),
                                                log.getGpCharged(),
                                                log.getStatus()))
                        .toList();

        return new TaxLogResponse(taxLogs.getTotalElements(), logs);
    }

    // 개별 processUserTax가 독립 트랜잭션으로 실행되도록 트랜잭션 없이 루프 실행
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public void processAllUsersTax() {
        List<Long> ownerIds =
                territoryRepository.findAllDistinctOwnerIds(Territory.TerritoryStatus.OCCUPIED);
        for (Long userId : ownerIds) {
            try {
                processUserTax(userId);
            } catch (Exception e) {
                log.error("토지세 처리 실패. userId={}", userId, e);
            }
        }
    }

    @Transactional
    public void processUserTax(Long userId) {
        int territoryCount = (int) territoryRepository.countByOwnerId(userId);
        if (territoryCount == 0) {
            return;
        }

        int taxAmount = calculateTaxAmount(userId, territoryCount);
        if (taxAmount == 0) {
            saveLog(userId, territoryCount, 0, TaxStatus.EXEMPT);
            return;
        }

        applyTaxOrEvict(userId, territoryCount, taxAmount);
    }

    private int calculateTaxAmount(Long userId, int territoryCount) {
        int seasonPassExemptBonus = resolveSeasonPassExemptBonus(userId);
        int effectiveExemptCount = LandTaxPolicy.BASE_EXEMPT_COUNT + seasonPassExemptBonus;
        int taxableCount = Math.max(0, territoryCount - effectiveExemptCount);
        return LandTaxPolicy.calculateDailyTax(taxableCount);
    }

    private void applyTaxOrEvict(Long userId, int territoryCount, int taxAmount) {
        // 위치별 GP 원칙의 유일한 예외 — 세금 회피 방지를 위해 금고에서 먼저,
        // 부족하면 영토 저장소에서 자동 수금한다.
        List<Long> territoryIds =
                territoryRepository
                        .findAllOccupiedByOwnerId(userId, Territory.TerritoryStatus.OCCUPIED)
                        .stream()
                        .map(Territory::getId)
                        .toList();
        boolean paid =
                combatResourceClient.chargeTax(
                        userId,
                        taxAmount,
                        territoryIds,
                        "LAND_TAX:" + userId + ":" + LocalDate.now());
        if (paid) {
            saveLog(userId, territoryCount, taxAmount, TaxStatus.PAID);
            clearGraceKey(userId);
            notificationService.sendNotification(
                    userId, NotificationType.TAX_CHARGED, "토지세 " + taxAmount + " GP가 정상 납부되었습니다.");
            log.info(
                    "토지세 납부 완료. userId={}, taxAmount={}, territoryCount={}",
                    userId,
                    taxAmount,
                    territoryCount);
            return;
        }

        String graceKey = GRACE_KEY_PREFIX + userId;
        boolean isInGrace = Boolean.TRUE.equals(redisTemplate.hasKey(graceKey));
        if (isInGrace) {
            saveLog(userId, territoryCount, 0, TaxStatus.FAILED);
            return;
        }

        // 유예기간 키가 없는 상태에서 이전 FAILED 로그가 있으면 유예기간이 만료된 것으로 판단
        boolean hadPreviousFail =
                landTaxLogRepository.existsByUserIdAndStatusAndChargedAtAfter(
                        userId, TaxStatus.FAILED, LocalDateTime.now().minusDays(2));
        if (hadPreviousFail) {
            saveLog(userId, territoryCount, 0, TaxStatus.EVICTED);
            enforceSelectiveEviction(userId, taxAmount);
        } else {
            saveLog(userId, territoryCount, 0, TaxStatus.FAILED);
            setGraceKey(userId);
            notificationService.sendNotification(
                    userId,
                    NotificationType.TAX_FAIL_WARNING,
                    "토지세 납부에 실패했습니다. "
                            + LandTaxPolicy.GRACE_PERIOD_HOURS
                            + "시간 내에 GP를 충전하지 않으면 영토가 강제 경매 전환됩니다.");
        }
    }

    private void enforceSelectiveEviction(Long userId, int taxAmount) {
        List<Territory> territories =
                territoryRepository.findAllOccupiedByOwnerId(
                        userId, Territory.TerritoryStatus.OCCUPIED);
        territories.sort(
                Comparator.comparingInt(
                        t -> GRADE_EVICTION_ORDER.getOrDefault(t.getGrade().getGrade(), 99)));
        LocalDateTime nextAuctionAt =
                LocalDateTime.now().plusHours(LandTaxPolicy.EVICTION_REAUCTION_DELAY_HOURS);
        int remaining = taxAmount;
        int evictedCount = 0;
        for (Territory territory : territories) {
            if (remaining <= 0) break;
            eventPublisher.publishEvent(new TerritoryLostEvent(territory.getId(), userId));
            territory.release(nextAuctionAt);
            int startPrice =
                    TerritoryPolicy.GRADE_BASE_PRICES.getOrDefault(
                            territory.getGrade().getGrade(), TerritoryPolicy.DEFAULT_BASE_PRICE);
            remaining -= startPrice;
            evictedCount++;
        }
        notificationService.sendNotification(
                userId, NotificationType.TAX_EVICTION, evictedCount + "개 영토가 강제 경매 전환됐습니다.");
        log.info(
                "토지세 미납 강제 경매 전환. userId={}, evictedCount={}, taxAmount={}",
                userId,
                evictedCount,
                taxAmount);
    }

    private void setGraceKey(Long userId) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(
                            GRACE_KEY_PREFIX + userId,
                            1,
                            Duration.ofHours(LandTaxPolicy.GRACE_PERIOD_HOURS));
        } catch (Exception e) {
            log.error("토지세 유예기간 Redis 키 저장 실패. userId={}", userId, e);
        }
    }

    private void clearGraceKey(Long userId) {
        try {
            redisTemplate.delete(GRACE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.error("토지세 유예기간 Redis 키 삭제 실패. userId={}", userId, e);
        }
    }

    private int resolveSeasonPassExemptBonus(Long userId) {
        return seasonQueryClient.getTaxExemptBonus(userId);
    }

    private void saveLog(Long userId, int territoryCount, int gpCharged, TaxStatus status) {
        User userRef = userRepository.getReferenceById(userId);
        landTaxLogRepository.save(
                LandTaxLog.builder()
                        .user(userRef)
                        .territoryCount(territoryCount)
                        .gpCharged(gpCharged)
                        .status(status)
                        .chargedAt(LocalDateTime.now())
                        .build());
    }
}
