package com.territorial.season.domain.season.service;

import com.territorial.season.domain.season.SeasonPassPolicy;
import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonPassProgress;
import com.territorial.season.domain.season.repository.SeasonPassProgressRepository;
import com.territorial.season.domain.season.repository.SeasonRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 패스 XP 적립. 게임 이벤트(경매 낙찰·공성 승리)는 모놀리식 relay가 /internal로 위임하며, 활성 시즌은 season-service가 자체 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonXpService {

    private static final String CACHE_PROGRESS = "season_pass:progress:";

    private final SeasonRepository seasonRepository;
    private final SeasonPassProgressRepository seasonPassProgressRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 게임 이벤트로 XP 적립 — 활성 시즌을 조회해 반영. 활성 시즌이 없으면 무시. */
    @Transactional
    public void grantXpForEvent(Long userId, int xpAmount) {
        seasonRepository
                .findActiveSeason(LocalDateTime.now())
                .ifPresent(season -> grantXpInternal(userId, season.getId(), xpAmount));
    }

    /** 미션 수령 등 직접 호출용 — 호출자 트랜잭션 내에서 XP 적립 후 진행도 반환. */
    public SeasonPassProgress grantXpDirectly(Long userId, Long seasonId, int xpAmount) {
        return grantXpInternal(userId, seasonId, xpAmount);
    }

    // 이벤트 컨텍스트 — 예외 대신 조용한 종료로 처리해야 하므로 if-isEmpty 패턴 의도적 사용
    private SeasonPassProgress grantXpInternal(Long userId, Long seasonId, int xpAmount) {
        Optional<Season> seasonOpt = seasonRepository.findById(seasonId);
        if (seasonOpt.isEmpty()) {
            log.warn("시즌 패스 XP 적립 실패: 시즌 없음. seasonId={}", seasonId);
            return null;
        }
        Season season = seasonOpt.get();

        SeasonPassProgress progress = findOrCreateProgress(userId, season);
        if (progress == null) return null;

        progress.addXp(xpAmount, SeasonPassPolicy.XP_PER_LEVEL);

        try {
            redisTemplate.delete(CACHE_PROGRESS + userId);
        } catch (Exception e) {
            log.warn("시즌 패스 XP 캐시 무효화 실패. userId={}", userId);
        }

        log.info("시즌 패스 XP 적립. userId={}, seasonId={}, xpAmount={}", userId, seasonId, xpAmount);
        return progress;
    }

    private SeasonPassProgress findOrCreateProgress(Long userId, Season season) {
        try {
            return seasonPassProgressRepository
                    .findByUserIdAndSeason_Id(userId, season.getId())
                    .orElseGet(
                            () ->
                                    seasonPassProgressRepository.save(
                                            SeasonPassProgress.builder()
                                                    .userId(userId)
                                                    .season(season)
                                                    .build()));
        } catch (DataIntegrityViolationException e) {
            return seasonPassProgressRepository
                    .findByUserIdAndSeason_Id(userId, season.getId())
                    .orElseGet(
                            () -> {
                                log.error(
                                        "동시 insert 이후 progress 재조회 실패. userId={}, seasonId={}",
                                        userId,
                                        season.getId());
                                return null;
                            });
        }
    }
}
