package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.AuctionQueryClient;
import com.territorial.auction.domain.admin.dto.AdminDashboardResponse;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import java.time.LocalDateTime;
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
    private final WalletRepository walletRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final SeasonRepository seasonRepository;

    public AdminDashboardResponse getDashboard() {
        LocalDateTime now = LocalDateTime.now();
        Season season = seasonRepository.findActiveSeason(now).orElse(null);
        return new AdminDashboardResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                auctionQueryClient.countActiveAuctions(),
                territoryRepository.countByStatus(Territory.TerritoryStatus.BIDDING),
                territoryRepository.countByStatus(Territory.TerritoryStatus.OCCUPIED),
                territoryRepository.countByStatus(Territory.TerritoryStatus.IDLE),
                walletRepository.sumAvailableAp(),
                globalVaultRepository.sumStoredGp() + buildingInstanceRepository.sumAllStoredGp(),
                season != null ? season.getSeasonNumber() : null,
                season != null ? season.getStartedAt() : null,
                season != null ? season.getEndedAt() : null);
    }
}
