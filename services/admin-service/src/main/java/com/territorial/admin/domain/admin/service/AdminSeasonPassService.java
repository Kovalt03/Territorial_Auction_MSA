package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.SeasonAdminClient;
import com.territorial.admin.client.SeasonAdminClient.SeasonPassView;
import com.territorial.admin.client.SeasonAdminClient.UpdateSeasonPassCommand;
import com.territorial.admin.domain.admin.dto.AdminSeasonPassResponse;
import com.territorial.admin.domain.admin.dto.AdminUpdateSeasonPassRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSeasonPassService {

    private final SeasonAdminClient seasonAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public List<AdminSeasonPassResponse> getSeasonPasses() {
        return seasonAdminClient.getSeasonPasses().stream()
                .map(AdminSeasonPassResponse::from)
                .toList();
    }

    @Transactional
    public AdminSeasonPassResponse update(
            Long adminUserId, Long seasonPassId, AdminUpdateSeasonPassRequest request) {
        SeasonPassView pass =
                seasonAdminClient.updateSeasonPass(
                        seasonPassId,
                        new UpdateSeasonPassCommand(
                                request.costAp(),
                                request.durationDays(),
                                request.islandBonusPct(),
                                request.extraBuilders(),
                                request.taxExemptBonus(),
                                request.buildTimeReductionPct()));

        adminAuditLogger.record(
                adminUserId,
                "SEASON_PASS_UPDATE",
                "SEASON_PASS",
                seasonPassId,
                Map.of("name", pass.name()));
        return AdminSeasonPassResponse.from(pass);
    }
}
