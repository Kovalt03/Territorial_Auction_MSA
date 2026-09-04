package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.client.CombatResourceClient;
import com.territorial.user.client.CombatResourceClient.UserSummary;
import com.territorial.user.client.MapTerritoryClient;
import com.territorial.user.client.SeasonQueryClient;
import com.territorial.user.client.SeasonQueryClient.UserPassSummary;
import com.territorial.user.client.SeasonTrophyClient;
import com.territorial.user.client.SeasonTrophyClient.Trophy;
import com.territorial.user.domain.user.dto.MyProfileResponse;
import com.territorial.user.domain.user.dto.MyTerritoryResponse;
import com.territorial.user.domain.user.dto.MyWalletResponse;
import com.territorial.user.domain.user.dto.PositionPair;
import com.territorial.user.domain.user.dto.UserProfileResponse;
import com.territorial.user.domain.user.dto.WalletSnapshot;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.UserProfile;
import com.territorial.user.domain.user.repository.UserProfileRepository;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필·지갑·보유 영토 합성 조회(BFF). user + combat·season·map 조회를 조합한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final WalletService walletService;
    private final CombatResourceClient combatResourceClient;
    private final SeasonQueryClient seasonQueryClient;
    private final SeasonTrophyClient seasonTrophyClient;
    private final MapTerritoryClient mapTerritoryClient;
    private final StringRedisTemplate stringRedisTemplate;

    public MyProfileResponse getMyProfile(Long userId) {
        User user = findUserOrThrow(userId);
        WalletSnapshot wallet = walletService.getWallet(userId);
        UserSummary combat = combatResourceClient.getUserSummary(userId);
        Optional<UserPassSummary> activePass = seasonQueryClient.getUserPassSummary(userId);
        int builderCount = 1 + activePass.map(UserPassSummary::extraBuilders).orElse(0);
        int territoryCount = (int) mapTerritoryClient.getOwnerCount(userId);

        MyProfileResponse.IslandInfo islandInfo =
                combat.islandId() == null
                        ? null
                        : new MyProfileResponse.IslandInfo(
                                combat.islandId(), combat.islandLevel(), 0, builderCount);

        return new MyProfileResponse(
                user.getId(),
                user.getNickname(),
                new MyProfileResponse.WalletInfo(
                        Math.toIntExact(combat.vaultGp()), wallet.availableAp(), wallet.lockedAp()),
                islandInfo,
                activePass
                        .map(p -> new MyProfileResponse.SeasonPassInfo(true, p.expiresAt()))
                        .orElse(new MyProfileResponse.SeasonPassInfo(false, null)),
                territoryCount);
    }

    public UserProfileResponse getUserProfile(Long userId) {
        User user = findUserOrThrow(userId);
        String profileImageUrl =
                userProfileRepository
                        .findById(userId)
                        .map(UserProfile::getProfileImageUrl)
                        .orElse(null);
        int level = combatResourceClient.getUserSummary(userId).islandLevel();
        int trophyPoints = seasonTrophyClient.getTrophy(userId).map(Trophy::score).orElse(0);
        int territoryCount = (int) mapTerritoryClient.getOwnerCount(userId);

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                level,
                trophyPoints,
                territoryCount,
                null,
                user.getCreatedAt());
    }

    public MyTerritoryResponse getMyTerritories(Long userId, Pageable pageable) {
        MapTerritoryClient.OwnerHoldingPage holdings =
                mapTerritoryClient.getOwnerHoldings(
                        userId, pageable.getPageNumber(), pageable.getPageSize());
        List<Long> ids =
                holdings.content().stream()
                        .map(MapTerritoryClient.OwnerHolding::territoryId)
                        .toList();

        Map<Long, Long> unitCounts = buildUnitCountMap(ids);
        Set<Long> invincibleIds = buildInvincibleSet(ids);

        List<MyTerritoryResponse.TerritoryInfo> territoryInfos =
                holdings.content().stream()
                        .map(
                                h ->
                                        new MyTerritoryResponse.TerritoryInfo(
                                                h.territoryId(),
                                                h.grade(),
                                                new PositionPair(h.coordX(), h.coordY()),
                                                h.continentName(),
                                                h.occupiedAt(),
                                                h.occupiedUntil(),
                                                unitCounts
                                                        .getOrDefault(h.territoryId(), 0L)
                                                        .intValue(),
                                                invincibleIds.contains(h.territoryId())))
                        .toList();
        return new MyTerritoryResponse((int) holdings.totalElements(), territoryInfos);
    }

    public MyWalletResponse getMyWallet(Long userId) {
        WalletSnapshot wallet = walletService.getWallet(userId);
        // GP 는 위치별 저장소·금고로 이관됐다 — 지갑 화면의 GP 는 금고 잔액을 보여준다.
        int vaultGp = Math.toIntExact(combatResourceClient.getUserSummary(userId).vaultGp());
        return new MyWalletResponse(vaultGp, wallet.availableAp(), wallet.lockedAp());
    }

    private Map<Long, Long> buildUnitCountMap(List<Long> territoryIds) {
        if (territoryIds.isEmpty()) return Map.of();
        return combatResourceClient.getTerritoryUnitCounts(territoryIds).stream()
                .collect(
                        Collectors.toMap(
                                CombatResourceClient.TerritoryUnitCount::territoryId,
                                CombatResourceClient.TerritoryUnitCount::unitCount));
    }

    private Set<Long> buildInvincibleSet(List<Long> territoryIds) {
        return territoryIds.stream()
                .filter(id -> Boolean.TRUE.equals(stringRedisTemplate.hasKey("invincible:" + id)))
                .collect(Collectors.toSet());
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
