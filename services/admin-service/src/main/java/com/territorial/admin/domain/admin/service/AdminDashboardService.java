package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.AuctionQueryClient;
import com.territorial.admin.client.CombatAdminClient;
import com.territorial.admin.client.MapAdminClient;
import com.territorial.admin.client.MapAdminClient.StatusCounts;
import com.territorial.admin.client.SeasonQueryClient;
import com.territorial.admin.client.SeasonQueryClient.ActiveSeason;
import com.territorial.admin.client.UserAdminClient;
import com.territorial.admin.client.UserAdminClient.UserCounts;
import com.territorial.admin.client.WalletClient;
import com.territorial.admin.domain.admin.dto.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserAdminClient userAdminClient;
    private final AuctionQueryClient auctionQueryClient;
    private final MapAdminClient mapAdminClient;
    private final WalletClient walletClient;
    private final CombatAdminClient combatAdminClient;
    private final SeasonQueryClient seasonQueryClient;

    public AdminDashboardResponse getDashboard() {
        ActiveSeason season = seasonQueryClient.getActiveSeason().orElse(null);
        StatusCounts territoryCounts = mapAdminClient.getStatusCounts();
        UserCounts userCounts = userAdminClient.counts();
        return new AdminDashboardResponse(
                userCounts.total(),
                userCounts.active(),
                userCounts.suspended(),
                auctionQueryClient.countActiveAuctions(),
                territoryCounts.biddingCount(),
                territoryCounts.occupiedCount(),
                territoryCounts.idleCount(),
                walletClient.sumAvailableAp(),
                combatAdminClient.getTotalStoredGp(),
                season != null ? season.seasonNumber() : null,
                season != null ? season.startedAt() : null,
                season != null ? season.endedAt() : null);
    }
}
