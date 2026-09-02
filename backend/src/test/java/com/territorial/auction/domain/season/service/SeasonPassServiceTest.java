package com.territorial.auction.domain.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.item.entity.UserItem;
import com.territorial.auction.domain.item.repository.ItemRepository;
import com.territorial.auction.domain.item.repository.UserItemRepository;
import com.territorial.auction.domain.season.dto.MySeasonPassResponse;
import com.territorial.auction.domain.season.dto.PurchaseLevelResponse;
import com.territorial.auction.domain.season.dto.PurchaseSeasonPassResponse;
import com.territorial.auction.domain.season.dto.SeasonPassResponse;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.SeasonPass;
import com.territorial.auction.domain.season.entity.SeasonPassLevelReward;
import com.territorial.auction.domain.season.entity.SeasonPassProgress;
import com.territorial.auction.domain.season.entity.UserSeasonPass;
import com.territorial.auction.domain.season.repository.SeasonPassLevelRewardRepository;
import com.territorial.auction.domain.season.repository.SeasonPassProgressRepository;
import com.territorial.auction.domain.season.repository.SeasonPassRepository;
import com.territorial.auction.domain.season.repository.SeasonPassRewardClaimRepository;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.season.repository.UserSeasonPassRepository;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonPassServiceTest {

    @InjectMocks private SeasonPassService seasonPassService;

    @Mock private SeasonRepository seasonRepository;
    @Mock private SeasonPassRepository seasonPassRepository;
    @Mock private UserSeasonPassRepository userSeasonPassRepository;
    @Mock private SeasonPassProgressRepository seasonPassProgressRepository;
    @Mock private SeasonPassLevelRewardRepository seasonPassLevelRewardRepository;
    @Mock private SeasonPassRewardClaimRepository seasonPassRewardClaimRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletClient walletClient;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @BeforeEach
    void setUpRedis() {
        // 예외 발생 테스트에서 Redis 호출 전에 throw되므로 lenient 처리
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("combat benefit은 활성 시즌 패스의 건설 혜택만 반환한다")
    void getCombatBenefit_activePass() {
        SeasonPass pass = buildSeasonPass(1L, 100, 30);
        ReflectionTestUtils.setField(pass, "buildTimeReductionPct", 20);
        User user =
                User.builder().username("u").email("u@x").passwordHash("h").nickname("u").build();
        UserSeasonPass active =
                UserSeasonPass.builder()
                        .user(user)
                        .seasonPass(pass)
                        .startedAt(LocalDateTime.now().minusDays(1))
                        .expiresAt(LocalDateTime.now().plusDays(1))
                        .build();
        given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                .willReturn(Optional.of(active));

        var response = seasonPassService.getCombatBenefit(1L);

        assertThat(response.buildTimeReductionPct()).isEqualTo(20);
        assertThat(response.extraBuilders()).isEqualTo(1);
    }

    @Test
    @DisplayName("combat benefit은 시즌 패스가 만료됐으면 기본값이다")
    void getCombatBenefit_expiredPass() {
        SeasonPass pass = buildSeasonPass(1L, 100, 30);
        User user =
                User.builder().username("u").email("u@x").passwordHash("h").nickname("u").build();
        UserSeasonPass expired =
                UserSeasonPass.builder()
                        .user(user)
                        .seasonPass(pass)
                        .startedAt(LocalDateTime.now().minusDays(2))
                        .expiresAt(LocalDateTime.now().minusDays(1))
                        .build();
        given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                .willReturn(Optional.of(expired));

        var response = seasonPassService.getCombatBenefit(1L);

        assertThat(response.buildTimeReductionPct()).isZero();
        assertThat(response.extraBuilders()).isZero();
    }

    // ─── 공통 픽스처 ─────────────────────────────────────────────────────────

    private SeasonPass buildSeasonPass(Long id, int costAp, int durationDays) {
        SeasonPass pass =
                SeasonPass.builder()
                        .name("Standard Pass")
                        .costAp(costAp)
                        .durationDays(durationDays)
                        .islandBonusPct(10)
                        .extraBuilders(1)
                        .taxExemptBonus(2)
                        .build();
        ReflectionTestUtils.setField(pass, "id", id);
        return pass;
    }

    private Season buildSeason(Long id, int number) {
        Season season =
                Season.builder()
                        .seasonNumber(number)
                        .startedAt(LocalDateTime.now().minusDays(30))
                        .endedAt(LocalDateTime.now().plusDays(30))
                        .build();
        ReflectionTestUtils.setField(season, "id", id);
        return season;
    }

    // ─── getMyPass() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyPass()")
    class GetMyPass {

        @Test
        @DisplayName("캐시 히트 - DB 조회 없이 캐시 반환")
        void cacheHit_returnsCachedResponse() {
            MySeasonPassResponse cached = new MySeasonPassResponse(false, null);
            given(valueOps.get("season_pass:my:1")).willReturn(cached);

            MySeasonPassResponse response = seasonPassService.getMyPass(1L);

            assertThat(response).isSameAs(cached);
            then(userSeasonPassRepository)
                    .should(never())
                    .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(any());
        }

        @Test
        @DisplayName("보유한 시즌패스 없음 - hasSeasonPass=false, seasonPass=null")
        void noPass_returnsFalse() {
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            MySeasonPassResponse response = seasonPassService.getMyPass(1L);

            assertThat(response.hasSeasonPass()).isFalse();
            assertThat(response.seasonPass()).isNull();
            then(valueOps).should().set(eq("season_pass:my:1"), any(), any());
        }

        @Test
        @DisplayName("유효한 시즌패스 존재 - 모든 필드 정확히 매핑")
        void validPass_allFieldsMappedCorrectly() {
            SeasonPass pass = buildSeasonPass(7L, 100, 30);
            LocalDateTime startedAt = LocalDateTime.now().minusDays(5);
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(25);
            UserSeasonPass userPass =
                    UserSeasonPass.builder()
                            .seasonPass(pass)
                            .startedAt(startedAt)
                            .expiresAt(expiresAt)
                            .build();

            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.of(userPass));

            MySeasonPassResponse response = seasonPassService.getMyPass(1L);

            assertThat(response.hasSeasonPass()).isTrue();
            MySeasonPassResponse.SeasonPassInfo info = response.seasonPass();
            assertThat(info.seasonPassId()).isEqualTo(7L);
            assertThat(info.name()).isEqualTo("Standard Pass");
            assertThat(info.startedAt()).isEqualTo(startedAt);
            assertThat(info.expiresAt()).isEqualTo(expiresAt);
            assertThat(info.daysRemaining()).isPositive();
            assertThat(info.benefits().islandBonusPct()).isEqualTo(10);
            assertThat(info.benefits().extraBuilders()).isEqualTo(1);
            assertThat(info.benefits().taxExemptBonus()).isEqualTo(2);
        }

        @Test
        @DisplayName("만료된 시즌패스 - hasSeasonPass=false, seasonPass=null")
        void expiredPass_returnsFalse() {
            SeasonPass pass = buildSeasonPass(1L, 100, 30);
            UserSeasonPass expiredPass =
                    UserSeasonPass.builder()
                            .seasonPass(pass)
                            .startedAt(LocalDateTime.now().minusDays(31))
                            .expiresAt(LocalDateTime.now().minusMinutes(1))
                            .build();

            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.of(expiredPass));

            MySeasonPassResponse response = seasonPassService.getMyPass(1L);

            assertThat(response.hasSeasonPass()).isFalse();
            assertThat(response.seasonPass()).isNull();
        }
    }

    // ─── purchase() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("purchase()")
    class Purchase {

        @Test
        @DisplayName("AP 충분 + 기존 패스 없음 - 새 UserSeasonPass 저장 후 캐시 갱신")
        void sufficientAp_noExisting_createsNew() {
            SeasonPass pass = buildSeasonPass(1L, 100, 30);
            User user = Mockito.mock(User.class);
            LocalDateTime beforePurchase = LocalDateTime.now();

            given(seasonPassRepository.findFirstByOrderByIdDesc()).willReturn(Optional.of(pass));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());
            given(seasonRepository.findActiveSeason(any()))
                    .willReturn(Optional.of(buildSeason(1L, 1)));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userSeasonPassRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(walletClient.spend(eq(1L), eq(100), anyString()))
                    .willReturn(new WalletSnapshot(400, 0));

            PurchaseSeasonPassResponse response = seasonPassService.purchase(1L);

            then(walletClient).should().spend(eq(1L), eq(100), anyString());
            then(userSeasonPassRepository).should().save(any(UserSeasonPass.class));
            then(valueOps).should().set(eq("season_pass:my:1"), any(), any());
            then(redisTemplate).should().delete("season_pass:progress:1");
            assertThat(response.seasonPassId()).isEqualTo(1L);
            assertThat(response.costAP()).isEqualTo(100);
            // 만료는 시즌 종료 시각(buildSeason: now+30일)에 종속된다
            assertThat(response.expiresAt()).isAfter(beforePurchase.plusDays(29));
        }

        @Test
        @DisplayName("AP 충분 + 유효한 기존 패스 존재 - SEASON_PASS_ALREADY_OWNED 예외")
        void sufficientAp_existingValid_throwsAlreadyOwned() {
            SeasonPass pass = buildSeasonPass(1L, 100, 30);
            UserSeasonPass existingPass = Mockito.mock(UserSeasonPass.class);
            given(existingPass.getExpiresAt()).willReturn(LocalDateTime.now().plusDays(10));

            given(seasonPassRepository.findFirstByOrderByIdDesc()).willReturn(Optional.of(pass));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.of(existingPass));

            assertThatThrownBy(() -> seasonPassService.purchase(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEASON_PASS_ALREADY_OWNED);
            then(userSeasonPassRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("AP 부족 - INSUFFICIENT_AP 예외")
        void insufficientAp_throwsException() {
            SeasonPass pass = buildSeasonPass(1L, 1000, 30);

            given(seasonPassRepository.findFirstByOrderByIdDesc()).willReturn(Optional.of(pass));
            given(seasonRepository.findActiveSeason(any()))
                    .willReturn(Optional.of(buildSeason(1L, 1)));
            given(userRepository.findById(1L)).willReturn(Optional.of(Mockito.mock(User.class)));
            given(userSeasonPassRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                    .given(walletClient)
                    .spend(eq(1L), eq(1000), anyString());

            assertThatThrownBy(() -> seasonPassService.purchase(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }

        @Test
        @DisplayName("시즌패스 없음 - SEASON_PASS_NOT_FOUND 예외")
        void noSeasonPass_throwsException() {
            given(seasonPassRepository.findFirstByOrderByIdDesc()).willReturn(Optional.empty());

            assertThatThrownBy(() -> seasonPassService.purchase(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEASON_PASS_NOT_FOUND);
        }
    }

    // ─── purchaseLevel() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("purchaseLevel()")
    class PurchaseLevel {

        @Test
        @DisplayName("AP 충분 + 최고 레벨 미만 - AP 차감 후 레벨 +1, XP 0")
        void sufficientAp_levelsUp() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassProgress progress =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(progress, "level", 5);
            ReflectionTestUtils.setField(progress, "xp", 300);
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(any(), any()))
                    .willReturn(Optional.of(progress));
            given(walletClient.spend(eq(1L), eq(500), anyString()))
                    .willReturn(new WalletSnapshot(500, 0));

            PurchaseLevelResponse response = seasonPassService.purchaseLevel(1L);

            assertThat(response.currentLevel()).isEqualTo(6);
            assertThat(response.currentXp()).isZero();
            assertThat(response.costAP()).isEqualTo(500);
            assertThat(response.remainingAP()).isEqualTo(500);
            then(redisTemplate).should().delete("season_pass:progress:1");
        }

        @Test
        @DisplayName("AP 부족 - INSUFFICIENT_AP 예외, 레벨 변동 없음")
        void insufficientAp_throwsException() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassProgress progress =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(progress, "level", 5);
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(any(), any()))
                    .willReturn(Optional.of(progress));
            willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                    .given(walletClient)
                    .spend(eq(1L), eq(500), anyString());

            // 런타임에는 @Transactional 롤백으로 레벨업이 취소된다(단위테스트는 예외 전파만 검증).
            assertThatThrownBy(() -> seasonPassService.purchaseLevel(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }

        @Test
        @DisplayName("최고 레벨 도달 - SEASON_LEVEL_MAX_REACHED 예외, AP 미차감")
        void maxLevel_throwsException() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassProgress progress =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(progress, "level", 30);

            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(any(), any()))
                    .willReturn(Optional.of(progress));

            assertThatThrownBy(() -> seasonPassService.purchaseLevel(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEASON_LEVEL_MAX_REACHED);
            then(walletClient).should(never()).spend(any(), anyInt(), anyString());
        }

        @Test
        @DisplayName("진행 중인 시즌 없음 - SEASON_NOT_FOUND 예외")
        void noActiveSeason_throwsException() {
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> seasonPassService.purchaseLevel(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEASON_NOT_FOUND);
        }
    }

    // ─── claimReward() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("claimReward()")
    class ClaimReward {

        private SeasonPassProgress progressAtLevel(User user, Season season, int level) {
            SeasonPassProgress progress =
                    SeasonPassProgress.builder().user(user).season(season).build();
            ReflectionTestUtils.setField(progress, "level", level);
            return progress;
        }

        @Test
        @DisplayName("ITEM 보상 - 인벤토리에 아이템 지급(신규 UserItem 저장) + 수령 기록")
        void itemReward_grantsItem() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassLevelReward reward =
                    SeasonPassLevelReward.builder()
                            .season(season)
                            .level(5)
                            .track(SeasonPassLevelReward.RewardTrack.FREE)
                            .rewardName("일반 공격권 x2")
                            .rewardKind(SeasonPassLevelReward.RewardKind.ITEM)
                            .itemType(Item.ItemType.ATTACK_NORMAL)
                            .quantity(2)
                            .build();
            ReflectionTestUtils.setField(reward, "id", 1L);
            Item item = Item.builder().name("일반 공격권").itemType(Item.ItemType.ATTACK_NORMAL).build();
            ReflectionTestUtils.setField(item, "id", 2L);

            given(seasonPassLevelRewardRepository.findById(1L)).willReturn(Optional.of(reward));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(progressAtLevel(user, season, 5)));
            given(seasonPassRewardClaimRepository.existsByUser_IdAndReward_Id(1L, 1L))
                    .willReturn(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(itemRepository.findByItemType(Item.ItemType.ATTACK_NORMAL))
                    .willReturn(Optional.of(item));
            given(userItemRepository.findByUser_IdAndItem_Id(any(), any()))
                    .willReturn(Optional.empty());

            seasonPassService.claimReward(1L, 1L);

            then(seasonPassRewardClaimRepository).should().save(any());
            then(userItemRepository).should().save(any(UserItem.class));
        }

        @Test
        @DisplayName("ITEM 보상 - 기존 보유 시 수량 증가")
        void itemReward_incrementsExisting() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassLevelReward reward =
                    SeasonPassLevelReward.builder()
                            .season(season)
                            .level(5)
                            .track(SeasonPassLevelReward.RewardTrack.FREE)
                            .rewardName("무적권 x3")
                            .rewardKind(SeasonPassLevelReward.RewardKind.ITEM)
                            .itemType(Item.ItemType.INVINCIBILITY)
                            .quantity(3)
                            .build();
            ReflectionTestUtils.setField(reward, "id", 1L);
            Item item =
                    Item.builder().name("무적 시간 추가권").itemType(Item.ItemType.INVINCIBILITY).build();
            ReflectionTestUtils.setField(item, "id", 2L);
            UserItem existing =
                    UserItem.builder()
                            .user(user)
                            .item(item)
                            .quantity(1)
                            .createdAt(LocalDateTime.now())
                            .build();

            given(seasonPassLevelRewardRepository.findById(1L)).willReturn(Optional.of(reward));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(progressAtLevel(user, season, 10)));
            given(seasonPassRewardClaimRepository.existsByUser_IdAndReward_Id(1L, 1L))
                    .willReturn(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(itemRepository.findByItemType(Item.ItemType.INVINCIBILITY))
                    .willReturn(Optional.of(item));
            given(userItemRepository.findByUser_IdAndItem_Id(any(), any()))
                    .willReturn(Optional.of(existing));

            seasonPassService.claimReward(1L, 1L);

            assertThat(existing.getQuantity()).isEqualTo(4);
            then(userItemRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("GP 보상 - 지갑에 GP 지급")
        void gpReward_grantsGp() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassLevelReward reward =
                    SeasonPassLevelReward.builder()
                            .season(season)
                            .level(10)
                            .track(SeasonPassLevelReward.RewardTrack.FREE)
                            .rewardName("GP 500")
                            .rewardKind(SeasonPassLevelReward.RewardKind.GP)
                            .quantity(500)
                            .build();
            ReflectionTestUtils.setField(reward, "id", 1L);
            GlobalVault vault = GlobalVault.builder().user(user).build();
            ReflectionTestUtils.setField(vault, "storedGp", 100);

            given(seasonPassLevelRewardRepository.findById(1L)).willReturn(Optional.of(reward));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(progressAtLevel(user, season, 10)));
            given(seasonPassRewardClaimRepository.existsByUser_IdAndReward_Id(1L, 1L))
                    .willReturn(false);
            given(user.getId()).willReturn(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            seasonPassService.claimReward(1L, 1L);

            assertThat(vault.getStoredGp()).isEqualTo(600);
            then(itemRepository).should(never()).findByItemType(any());
        }

        @Test
        @DisplayName("이미 수령한 보상 - REWARD_ALREADY_CLAIMED, 지급 없음")
        void alreadyClaimed_throwsAndDoesNotGrant() {
            User user = Mockito.mock(User.class);
            Season season = buildSeason(1L, 1);
            SeasonPassLevelReward reward =
                    SeasonPassLevelReward.builder()
                            .season(season)
                            .level(5)
                            .track(SeasonPassLevelReward.RewardTrack.FREE)
                            .rewardName("일반 공격권 x1")
                            .rewardKind(SeasonPassLevelReward.RewardKind.ITEM)
                            .itemType(Item.ItemType.ATTACK_NORMAL)
                            .quantity(1)
                            .build();
            ReflectionTestUtils.setField(reward, "id", 1L);

            given(seasonPassLevelRewardRepository.findById(1L)).willReturn(Optional.of(reward));
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(progressAtLevel(user, season, 5)));
            given(seasonPassRewardClaimRepository.existsByUser_IdAndReward_Id(1L, 1L))
                    .willReturn(true);

            assertThatThrownBy(() -> seasonPassService.claimReward(1L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REWARD_ALREADY_CLAIMED);
            then(userItemRepository).should(never()).save(any());
        }
    }

    // ─── getProgress() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProgress()")
    class GetProgress {

        @Test
        @DisplayName("캐시 히트 - DB 조회 없이 캐시 반환")
        void cacheHit_returnsCachedResponse() {
            SeasonPassResponse cached =
                    new SeasonPassResponse(
                            1L, "Season 1", "FREE", 1, 0, 1000, 1000, 500, null, List.of());
            given(valueOps.get("season_pass:progress:1")).willReturn(cached);

            SeasonPassResponse response = seasonPassService.getProgress(1L);

            assertThat(response).isSameAs(cached);
            then(seasonRepository).should(never()).findActiveSeason(any());
        }

        @Test
        @DisplayName("시즌패스 보유 유저 - passType=PREMIUM")
        void withSeasonPass_returnsPremium() {
            Season season = buildSeason(3L, 3);
            SeasonPass pass = buildSeasonPass(1L, 100, 30);
            UserSeasonPass userPass =
                    UserSeasonPass.builder()
                            .seasonPass(pass)
                            .startedAt(LocalDateTime.now().minusDays(5))
                            .expiresAt(LocalDateTime.now().plusDays(25))
                            .build();

            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.of(userPass));

            SeasonPassResponse response = seasonPassService.getProgress(1L);

            assertThat(response.seasonId()).isEqualTo(3L);
            assertThat(response.seasonName()).isEqualTo("Season 3");
            assertThat(response.passType()).isEqualTo("PREMIUM");
            assertThat(response.seasonEndsAt()).isEqualTo(season.getEndedAt());
        }

        @Test
        @DisplayName("시즌패스 미보유 유저 - passType=FREE")
        void withoutSeasonPass_returnsFree() {
            Season season = buildSeason(3L, 3);

            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            SeasonPassResponse response = seasonPassService.getProgress(1L);

            assertThat(response.passType()).isEqualTo("FREE");
        }

        @Test
        @DisplayName("progress 없음 - 기본값(level=1, xp=0) 반환")
        void noProgress_returnsDefaults() {
            Season season = buildSeason(1L, 1);

            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            SeasonPassResponse response = seasonPassService.getProgress(1L);

            assertThat(response.currentLevel()).isEqualTo(1);
            assertThat(response.currentXp()).isEqualTo(0);
            assertThat(response.nextLevelXp()).isEqualTo(1000);
        }

        @Test
        @DisplayName("progress 있음 - 저장된 레벨/XP 반환")
        void withProgress_returnsStoredValues() {
            Season season = buildSeason(1L, 1);
            SeasonPassProgress progress = SeasonPassProgress.builder().build();
            ReflectionTestUtils.setField(progress, "level", 5);
            ReflectionTestUtils.setField(progress, "xp", 300);

            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());
            given(seasonPassProgressRepository.findByUser_IdAndSeason_Id(1L, 1L))
                    .willReturn(Optional.of(progress));

            SeasonPassResponse response = seasonPassService.getProgress(1L);

            assertThat(response.currentLevel()).isEqualTo(5);
            assertThat(response.currentXp()).isEqualTo(300);
        }

        @Test
        @DisplayName("레벨 보상 목록 - isClaimed 정확히 반영")
        void rewards_claimedStatusMappedCorrectly() {
            Season season = buildSeason(1L, 1);
            SeasonPassLevelReward reward1 =
                    SeasonPassLevelReward.builder()
                            .season(season)
                            .level(5)
                            .rewardName("공격 토큰 x2")
                            .build();
            SeasonPassLevelReward reward2 =
                    SeasonPassLevelReward.builder()
                            .season(season)
                            .level(10)
                            .rewardName("전설 스킨")
                            .build();
            ReflectionTestUtils.setField(reward1, "id", 1L);
            ReflectionTestUtils.setField(reward2, "id", 2L);

            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.of(season));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());
            given(seasonPassLevelRewardRepository.findBySeason_IdOrderByLevelAsc(1L))
                    .willReturn(List.of(reward1, reward2));
            given(seasonPassRewardClaimRepository.findClaimedRewardIdsByUserIdAndSeasonId(1L, 1L))
                    .willReturn(Set.of(1L)); // reward1만 수령

            SeasonPassResponse response = seasonPassService.getProgress(1L);

            assertThat(response.rewards()).hasSize(2);
            assertThat(response.rewards().get(0).isClaimed()).isTrue();
            assertThat(response.rewards().get(1).isClaimed()).isFalse();
        }

        @Test
        @DisplayName("진행 중인 시즌 없음 - SEASON_NOT_FOUND 예외")
        void noActiveSeason_throwsException() {
            given(seasonRepository.findActiveSeason(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> seasonPassService.getProgress(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEASON_NOT_FOUND);
        }
    }
}
