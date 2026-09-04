package com.territorial.auction.domain.user.service;

import com.territorial.auction.domain.combat.client.CombatResourceClient;
import com.territorial.auction.domain.combat.client.CombatResourceClient.UserSummary;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.dto.*;
import com.territorial.auction.domain.user.dto.MyWalletResponse;
import com.territorial.auction.domain.user.entity.*;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.MapTerritoryClient;
import com.territorial.auction.global.client.SeasonQueryClient;
import com.territorial.auction.global.client.SeasonQueryClient.UserPassSummary;
import com.territorial.auction.global.client.SeasonTrophyClient;
import com.territorial.auction.global.client.SeasonTrophyClient.Trophy;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final WalletClient walletClient;
    private final CombatResourceClient combatResourceClient;
    private final SeasonQueryClient seasonQueryClient;
    private final MapTerritoryClient mapTerritoryClient;
    private final UserProfileRepository userProfileRepository;
    private final SeasonTrophyClient seasonTrophyClient;
    private final StringRedisTemplate stringRedisTemplate;

    public User findById(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public User findByEmail(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public MyProfileResponse getMyProfile(Long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        WalletSnapshot wallet = walletClient.getWallet(userId);

        UserSummary combat = combatResourceClient.getUserSummary(userId);

        // TODO: Redis 캐시 우선 조회 후 미존재 시 DB 조회로 전환 필요 (TTL 30분)
        //       명세: https://www.notion.so/33c2efa4278d81a88cf3eff675a30e46 비고 참고
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
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public MyWalletResponse getMyWallet(Long userId) {
        WalletSnapshot wallet = walletClient.getWallet(userId);
        // GP 는 위치별 저장소·금고로 이관됐다 — 지갑 화면의 GP 는 금고 잔액을 보여준다.
        int vaultGp = Math.toIntExact(combatResourceClient.getUserSummary(userId).vaultGp());
        return new MyWalletResponse(vaultGp, wallet.availableAp(), wallet.lockedAp());
    }
}
