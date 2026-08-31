package com.territorial.auction.domain.user.service;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.map.TerritoryPolicy;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.repository.UnitInstanceRepository;
import com.territorial.auction.domain.season.entity.UserSeasonPass;
import com.territorial.auction.domain.season.entity.UserTrophy;
import com.territorial.auction.domain.season.repository.UserSeasonPassRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.dto.*;
import com.territorial.auction.domain.user.dto.MyWalletResponse;
import com.territorial.auction.domain.user.entity.*;
import com.territorial.auction.domain.user.repository.NotificationSettingRepository;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.JwtAuthenticationFilter;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final WalletClient walletClient;
    private final GlobalVaultRepository globalVaultRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final UserSeasonPassRepository userSeasonPassRepository;
    private final TerritoryRepository territoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTrophyRepository userTrophyRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final UnitInstanceRepository unitInstanceRepository;

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

        Optional<HomeIsland> islandOpt = homeIslandRepository.findByUserId(userId);

        // TODO: Redis 캐시 우선 조회 후 미존재 시 DB 조회로 전환 필요 (TTL 30분)
        //       명세: https://www.notion.so/33c2efa4278d81a88cf3eff675a30e46 비고 참고
        Optional<UserSeasonPass> activePass =
                userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(userId);

        int builderCount = 1 + activePass.map(p -> p.getSeasonPass().getExtraBuilders()).orElse(0);

        int territoryCount = (int) territoryRepository.countByOwnerId(userId);

        MyProfileResponse.IslandInfo islandInfo =
                islandOpt
                        .map(
                                island ->
                                        new MyProfileResponse.IslandInfo(
                                                island.getId(),
                                                island.getLevel(),
                                                0, // TODO: 섬 건물 합산 생산량 계산으로 교체 필요
                                                builderCount))
                        .orElse(null);

        return new MyProfileResponse(
                user.getId(),
                user.getNickname(),
                new MyProfileResponse.WalletInfo(
                        vaultGp(user.getId()), wallet.availableAp(), wallet.lockedAp()),
                islandInfo,
                activePass
                        .map(
                                p ->
                                        new MyProfileResponse.SeasonPassInfo(
                                                p.getIsActive(), p.getExpiresAt()))
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

        int level = homeIslandRepository.findByUserId(userId).map(HomeIsland::getLevel).orElse(1);

        int trophyPoints =
                userTrophyRepository.findById(userId).map(UserTrophy::getScore).orElse(0);

        int territoryCount = (int) territoryRepository.countByOwnerId(userId);

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

    @Transactional
    public void deleteMe(Long userId, String password, String accessToken) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.getPasswordHash().isEmpty()
                && !passwordEncoder.matches(password, user.getPasswordHash()))
            throw new CustomException(ErrorCode.INVALID_PASSWORD);

        user.updateStatus(UserStatus.WITHDRAWN);
        userRepository.save(user);
        refreshTokenService.delete(userId);
        blacklistAccessToken(accessToken);
    }

    private void blacklistAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) return;
        long remainingMs = jwtTokenProvider.getRemainingMs(accessToken);
        if (remainingMs <= 0) return;
        stringRedisTemplate
                .opsForValue()
                .set(
                        JwtAuthenticationFilter.BLACKLIST_KEY_PREFIX + accessToken,
                        "1",
                        Duration.ofMillis(remainingMs));
    }

    public NotificationSettingResponse getNotificationSetting(Long userId) {
        NotificationSetting setting =
                notificationSettingRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        return NotificationSettingResponse.from(setting);
    }

    @Transactional
    public NotificationSettingResponse updateNotificationSetting(
            Long userId, UpdateNotificationSettingRequest request) {
        NotificationSetting setting =
                notificationSettingRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        setting.update(
                request.isOutbidEnabled(),
                request.isAuctionStartEnabled(),
                request.isMarketingEnabled());
        notificationSettingRepository.save(setting);
        return NotificationSettingResponse.from(setting);
    }

    @Transactional(readOnly = true)
    public MyTerritoryResponse getMyTerritories(Long userId, Pageable pageable) {
        Page<Territory> territoryPage = territoryRepository.findAllByUserId(userId, pageable);
        List<Long> ids = territoryPage.getContent().stream().map(Territory::getId).toList();

        Map<Long, Long> unitCounts = buildUnitCountMap(ids);
        Set<Long> invincibleIds = buildInvincibleSet(ids);

        List<MyTerritoryResponse.TerritoryInfo> territoryInfos =
                territoryPage.getContent().stream()
                        .map(
                                t ->
                                        new MyTerritoryResponse.TerritoryInfo(
                                                t.getId(),
                                                t.getGrade().getGrade(),
                                                new PositionPair(t.getCoordX(), t.getCoordY()),
                                                t.getContinent().getDisplayName(),
                                                deriveOccupiedAt(t),
                                                t.getOccupiedUntil(),
                                                unitCounts.getOrDefault(t.getId(), 0L).intValue(),
                                                invincibleIds.contains(t.getId())))
                        .toList();
        return new MyTerritoryResponse((int) territoryPage.getTotalElements(), territoryInfos);
    }

    private LocalDateTime deriveOccupiedAt(Territory t) {
        if (t.getOccupiedUntil() == null) return null;
        return t.getOccupiedUntil().minusDays(TerritoryPolicy.OCCUPATION_DURATION_DAYS);
    }

    private Map<Long, Long> buildUnitCountMap(List<Long> territoryIds) {
        if (territoryIds.isEmpty()) return Map.of();
        return unitInstanceRepository.sumQuantityGroupByTerritoryIds(territoryIds).stream()
                .collect(
                        Collectors.toMap(
                                row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
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
        return new MyWalletResponse(vaultGp(userId), wallet.availableAp(), wallet.lockedAp());
    }

    private int vaultGp(Long userId) {
        return globalVaultRepository.findById(userId).map(GlobalVault::getStoredGp).orElse(0);
    }
}
