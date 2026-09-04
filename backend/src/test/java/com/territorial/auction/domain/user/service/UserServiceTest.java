package com.territorial.auction.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.territorial.auction.domain.combat.client.CombatResourceClient;
import com.territorial.auction.domain.combat.client.CombatResourceClient.UserSummary;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.dto.MyProfileResponse;
import com.territorial.auction.domain.user.dto.MyTerritoryResponse;
import com.territorial.auction.domain.user.dto.MyWalletResponse;
import com.territorial.auction.domain.user.dto.UserProfileResponse;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserProfile;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.client.MapTerritoryClient;
import com.territorial.auction.global.client.MapTerritoryClient.OwnerHolding;
import com.territorial.auction.global.client.MapTerritoryClient.OwnerHoldingPage;
import com.territorial.auction.global.client.SeasonQueryClient;
import com.territorial.auction.global.client.SeasonQueryClient.UserPassSummary;
import com.territorial.auction.global.client.SeasonTrophyClient;
import com.territorial.auction.global.client.SeasonTrophyClient.Trophy;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private WalletClient walletClient;

    @Mock private CombatResourceClient combatResourceClient;
    @Mock private SeasonQueryClient seasonQueryClient;
    @Mock private MapTerritoryClient mapTerritoryClient;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private SeasonTrophyClient seasonTrophyClient;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private com.territorial.auction.global.security.jwt.JwtTokenProvider jwtTokenProvider;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUpCombatSummary() {
        org.mockito.Mockito.lenient()
                .when(combatResourceClient.getUserSummary(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new UserSummary(0, null, 1));
    }

    // ─── 공통 픽스처 ─────────────────────────────────────────────────────────

    private User sampleUser() {
        User user =
                User.builder()
                        .username("testuser")
                        .email("user@example.com")
                        .passwordHash("encoded")
                        .nickname("픽셀전사")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 1, 10, 0, 0));
        return user;
    }

    private UserProfile sampleUserProfile(User user) {
        UserProfile profile = UserProfile.builder().user(user).build();
        ReflectionTestUtils.setField(profile, "profileImageUrl", "https://cdn.example.com/1.png");
        return profile;
    }

    // ─── getMyProfile() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyProfile()")
    class GetMyProfile {

        @Test
        @DisplayName("정상 조회 시 MyProfileResponse 반환")
        void getMyProfile_success() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(300, 0));
            given(combatResourceClient.getUserSummary(1L)).willReturn(new UserSummary(1500, 1L, 1));
            given(seasonQueryClient.getUserPassSummary(1L)).willReturn(Optional.empty());
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(3L);

            MyProfileResponse response = userService.getMyProfile(1L);

            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.nickname()).isEqualTo("픽셀전사");
            assertThat(response.wallet().availableGP()).isEqualTo(1500);
            assertThat(response.wallet().availableAP()).isEqualTo(300);
            assertThat(response.wallet().lockedAP()).isEqualTo(0);
            assertThat(response.island().islandId()).isEqualTo(1L);
            assertThat(response.seasonPass().isActive()).isFalse();
            assertThat(response.territoryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("활성 시즌 패스가 있으면 seasonPass.isActive = true")
        void getMyProfile_withActiveSeasonPass() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(300, 0));
            given(combatResourceClient.getUserSummary(1L)).willReturn(new UserSummary(0, 1L, 1));
            given(seasonQueryClient.getUserPassSummary(1L))
                    .willReturn(
                            Optional.of(new UserPassSummary(LocalDateTime.now().plusDays(30), 1)));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            MyProfileResponse response = userService.getMyProfile(1L);

            assertThat(response.seasonPass().isActive()).isTrue();
            assertThat(response.seasonPass().expiresAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 userId 시 USER_NOT_FOUND 예외")
        void getMyProfile_userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyProfile(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("섬 미생성 유저는 islandInfo null로 정상 반환")
        void getMyProfile_islandNotFound() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(300, 0));
            given(combatResourceClient.getUserSummary(1L)).willReturn(new UserSummary(0, null, 1));
            given(seasonQueryClient.getUserPassSummary(1L)).willReturn(Optional.empty());
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            MyProfileResponse response = userService.getMyProfile(1L);

            assertThat(response.island()).isNull();
        }
    }

    // ─── getUserProfile() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserProfile()")
    class GetUserProfile {

        @Test
        @DisplayName("정상 조회 시 UserProfileResponse 반환")
        void getUserProfile_success() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(5L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.nickname()).isEqualTo("픽셀전사");
            assertThat(response.profileImageUrl()).isNull();
            assertThat(response.territoryCount()).isEqualTo(5);
            assertThat(response.joinedAt()).isEqualTo(LocalDateTime.of(2026, 1, 10, 0, 0));
        }

        @Test
        @DisplayName("profileImageUrl이 user_profiles에서 반환")
        void getUserProfile_profileImageReturned() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userProfileRepository.findById(1L))
                    .willReturn(Optional.of(sampleUserProfile(user)));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/1.png");
        }

        @Test
        @DisplayName("user_profiles 없으면 profileImageUrl = null")
        void getUserProfile_noProfileImage_returnsNull() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.profileImageUrl()).isNull();
        }

        @Test
        @DisplayName("trophyPoints가 user_trophies.score에서 반환")
        void getUserProfile_trophyPointsReturned() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(seasonTrophyClient.getTrophy(1L))
                    .willReturn(Optional.of(new Trophy(1L, 3850, "BRONZE")));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.trophyPoints()).isEqualTo(3850);
        }

        @Test
        @DisplayName("트로피 기록 없으면 trophyPoints = 0")
        void getUserProfile_noTrophy_returns0() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.trophyPoints()).isEqualTo(0);
        }

        @Test
        @DisplayName("level이 home_islands.level에서 반환")
        void getUserProfile_levelReturned() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(combatResourceClient.getUserSummary(1L)).willReturn(new UserSummary(0, 1L, 5));
            given(mapTerritoryClient.getOwnerCount(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.level()).isEqualTo(5);
        }

        @Test
        @DisplayName("존재하지 않는 userId 시 USER_NOT_FOUND 예외")
        void getUserProfile_userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserProfile(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMyWallet()")
    class GetMyWallet {

        @Test
        @DisplayName("정상 조회 시 MyWalletResponse 반환")
        void getMyWallet_success() {
            User user = sampleUser();

            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(300, 0));

            given(combatResourceClient.getUserSummary(1L)).willReturn(new UserSummary(1500, 1L, 1));
            MyWalletResponse response = userService.getMyWallet(1L);

            assertThat(response.availableGP()).isEqualTo(1500);
            assertThat(response.availableAP()).isEqualTo(300);
            assertThat(response.lockedAP()).isEqualTo(0);
        }

        @Test
        @DisplayName("지갑 없으면 USER_NOT_FOUND 예외")
        void getMyWallet_notFound() {
            willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                    .given(walletClient)
                    .getWallet(99L);

            assertThatThrownBy(() -> userService.getMyWallet(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ─── getMyTerritories() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyTerritories()")
    class GetMyTerritories {

        private OwnerHolding sampleHolding() {
            return new OwnerHolding(10L, "A", 3, 7, "아시아", null, null);
        }

        @Test
        @DisplayName("영토가 있으면 totalCount와 territoryInfos 반환")
        void getMyTerritories_success() {
            PageRequest pageable = PageRequest.of(0, 10);
            given(mapTerritoryClient.getOwnerHoldings(1L, 0, 10))
                    .willReturn(new OwnerHoldingPage(List.of(sampleHolding()), 1));

            MyTerritoryResponse response = userService.getMyTerritories(1L, pageable);

            assertThat(response.totalCount()).isEqualTo(1);
            assertThat(response.territories()).hasSize(1);

            MyTerritoryResponse.TerritoryInfo info = response.territories().get(0);
            assertThat(info.territoryId()).isEqualTo(10L);
            assertThat(info.grade()).isEqualTo("A");
            assertThat(info.position().x()).isEqualTo(3);
            assertThat(info.position().y()).isEqualTo(7);
            assertThat(info.continentName()).isEqualTo("아시아");
        }

        @Test
        @DisplayName("영토가 없으면 totalCount=0, 빈 목록 반환")
        void getMyTerritories_empty() {
            PageRequest pageable = PageRequest.of(0, 10);
            given(mapTerritoryClient.getOwnerHoldings(1L, 0, 10))
                    .willReturn(new OwnerHoldingPage(Collections.emptyList(), 0));

            MyTerritoryResponse response = userService.getMyTerritories(1L, pageable);

            assertThat(response.totalCount()).isEqualTo(0);
            assertThat(response.territories()).isEmpty();
        }
    }
}
