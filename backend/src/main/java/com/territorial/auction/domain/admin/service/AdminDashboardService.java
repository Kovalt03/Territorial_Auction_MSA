package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.AuctionQueryClient;
import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.dto.AdminDashboardResponse;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.SeasonQueryClient;
import com.territorial.auction.global.client.SeasonQueryClient.ActiveSeason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final AuctionQueryClient auctionQueryClient;
    private final TerritoryRepository territoryRepository;
    private final WalletClient walletClient;
    private final CombatAdminClient combatAdminClient;
    private final SeasonQueryClient seasonQueryClient;

    public AdminDashboardResponse getDashboard() {
        ActiveSeason season = seasonQueryClient.getActiveSeason().orElse(null);
        return new AdminDashboardResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                auctionQueryClient.countActiveAuctions(),
                territoryRepository.countByStatus(Territory.TerritoryStatus.BIDDING),
                territoryRepository.countByStatus(Territory.TerritoryStatus.OCCUPIED),
                territoryRepository.countByStatus(Territory.TerritoryStatus.IDLE),
                walletClient.sumAvailableAp(),
                combatAdminClient.getTotalStoredGp(),
                season != null ? season.seasonNumber() : null,
                season != null ? season.startedAt() : null,
                season != null ? season.endedAt() : null);
    }
}
