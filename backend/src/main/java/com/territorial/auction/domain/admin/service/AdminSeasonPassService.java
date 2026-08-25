package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminSeasonPassResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateSeasonPassRequest;
import com.territorial.auction.domain.season.entity.SeasonPass;
import com.territorial.auction.domain.season.repository.SeasonPassRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSeasonPassService {

    private final SeasonPassRepository seasonPassRepository;
    private final AdminAuditLogger adminAuditLogger;

    public List<AdminSeasonPassResponse> getSeasonPasses() {
        return seasonPassRepository.findAll().stream().map(AdminSeasonPassResponse::from).toList();
    }

    @Transactional
    public AdminSeasonPassResponse update(
            Long adminUserId, Long seasonPassId, AdminUpdateSeasonPassRequest request) {
        SeasonPass pass = findOrThrow(seasonPassId);
        pass.update(
                request.costAp(),
                request.durationDays(),
                request.islandBonusPct(),
                request.extraBuilders(),
                request.taxExemptBonus(),
                request.buildTimeReductionPct());

        adminAuditLogger.record(
                adminUserId,
                "SEASON_PASS_UPDATE",
                "SEASON_PASS",
                seasonPassId,
                Map.of("name", pass.getName()));
        return AdminSeasonPassResponse.from(pass);
    }

    private SeasonPass findOrThrow(Long seasonPassId) {
        return seasonPassRepository
                .findById(seasonPassId)
                .orElseThrow(() -> new CustomException(ErrorCode.SEASON_PASS_NOT_FOUND));
    }
}
