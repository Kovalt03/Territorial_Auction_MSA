package com.territorial.season.internal;

import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonPass;
import com.territorial.season.domain.season.repository.SeasonPassRepository;
import com.territorial.season.domain.season.repository.SeasonRepository;
import com.territorial.season.global.exception.ErrorCode;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminCreateSeasonRequest;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminSeasonPassView;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminSeasonView;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminUpdateSeasonPassRequest;
import com.territorial.auction.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** admin(모놀리식) 시즌·시즌패스 관리 위임 처리. 감사 로그는 모놀리식 admin 레이어가 남긴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSeasonInternalService {

    private final SeasonRepository seasonRepository;
    private final SeasonPassRepository seasonPassRepository;

    public List<AdminSeasonView> getSeasons() {
        return seasonRepository.findAllByOrderBySeasonNumberDesc().stream()
                .map(AdminSeasonView::from)
                .toList();
    }

    // 새 시즌 생성/시작. 진행 중 시즌이 있으면 거부(동시 활성 시즌 방지). 번호는 자동 증가.
    @Transactional
    public AdminSeasonView createSeason(AdminCreateSeasonRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (seasonRepository.findActiveSeason(now).isPresent()) {
            throw new CustomException(ErrorCode.SEASON_ALREADY_ACTIVE);
        }
        LocalDateTime startedAt = request.startedAt() != null ? request.startedAt() : now;
        int seasonNumber = seasonRepository.findMaxSeasonNumber() + 1;
        Season season =
                seasonRepository.save(
                        Season.builder()
                                .seasonNumber(seasonNumber)
                                .startedAt(startedAt)
                                .endedAt(request.endedAt())
                                .build());
        return AdminSeasonView.from(season);
    }

    // 시즌 즉시 종료. endedAt을 현재로 설정 → SeasonEndScheduler가 정산.
    @Transactional
    public AdminSeasonView endSeason(Long seasonId) {
        Season season =
                seasonRepository
                        .findById(seasonId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_BY_ID_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (season.getEndedAt() != null && season.getEndedAt().isBefore(now)) {
            throw new CustomException(ErrorCode.SEASON_ALREADY_ENDED);
        }
        season.endNow(now);
        return AdminSeasonView.from(season);
    }

    public List<AdminSeasonPassView> getSeasonPasses() {
        return seasonPassRepository.findAll().stream().map(AdminSeasonPassView::from).toList();
    }

    @Transactional
    public AdminSeasonPassView updateSeasonPass(
            Long seasonPassId, AdminUpdateSeasonPassRequest request) {
        SeasonPass pass =
                seasonPassRepository
                        .findById(seasonPassId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_PASS_NOT_FOUND));
        pass.update(
                request.costAp(),
                request.durationDays(),
                request.islandBonusPct(),
                request.extraBuilders(),
                request.taxExemptBonus(),
                request.buildTimeReductionPct());
        return AdminSeasonPassView.from(pass);
    }
}
