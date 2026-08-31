package com.territorial.auction.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.season.entity.SeasonPass;
import com.territorial.auction.domain.season.entity.UserSeasonPass;
import com.territorial.auction.domain.season.entity.UserTrophy;
import com.territorial.auction.domain.season.repository.UserSeasonPassRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.user.client.WalletClient;
import com.territorial.auction.domain.user.client.WalletSnapshot;
import com.territorial.auction.domain.user.dto.ChangeNicknameResponse;
import com.territorial.auction.domain.user.dto.MyProfileResponse;
import com.territorial.auction.domain.user.dto.MyTerritoryResponse;
import com.territorial.auction.domain.user.dto.MyWalletResponse;
import com.territorial.auction.domain.user.dto.NotificationSettingResponse;
import com.territorial.auction.domain.user.dto.UpdateNotificationSettingRequest;
import com.territorial.auction.domain.user.dto.UserProfileResponse;
import com.territorial.auction.domain.user.entity.NotificationSetting;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserProfile;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.repository.NotificationSettingRepository;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private WalletClient walletClient;

    @Mock
    private com.territorial.auction.domain.building.repository.GlobalVaultRepository
            globalVaultRepository;

    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private UserSeasonPassRepository userSeasonPassRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserTrophyRepository userTrophyRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private com.territorial.auction.global.security.jwt.JwtTokenProvider jwtTokenProvider;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private com.territorial.auction.domain.military.repository.UnitInstanceRepository
            unitInstanceRepository;

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

    // 지갑 화면의 GP 는 금고 잔액을 보여준다 — 금고에 1500 을 둔다.
    private void stubVaultGp(Long userId, int gp) {
        com.territorial.auction.domain.building.entity.GlobalVault vault =
                com.territorial.auction.domain.building.entity.GlobalVault.builder().build();
        ReflectionTestUtils.setField(vault, "storedGp", gp);
        given(globalVaultRepository.findById(userId)).willReturn(java.util.Optional.of(vault));
    }

    private HomeIsland sampleIsland(User user) {
        HomeIsland island = HomeIsland.builder().user(user).build();
        ReflectionTestUtils.setField(island, "id", 1L);
        return island;
    }

    private UserProfile sampleUserProfile(User user) {
        UserProfile profile = UserProfile.builder().user(user).build();
        ReflectionTestUtils.setField(profile, "profileImageUrl", "https://cdn.example.com/1.png");
        return profile;
    }

    private UserTrophy sampleUserTrophy(User user) {
        UserTrophy trophy = UserTrophy.builder().user(user).season(null).build();
        ReflectionTestUtils.setField(trophy, "score", 3850);
        return trophy;
    }

    private SeasonPass sampleSeasonPass() {
        return SeasonPass.builder()
                .name("기본 시즌 패스")
                .costAp(100)
                .islandBonusPct(10)
                .extraBuilders(1)
                .build();
    }

    private NotificationSetting sampleNotificationSetting(User user) {
        NotificationSetting setting = NotificationSetting.builder().user(user).build();
        ReflectionTestUtils.setField(setting, "updatedAt", LocalDateTime.of(2026, 4, 9, 10, 0));
        return setting;
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
            given(homeIslandRepository.findByUserId(1L))
                    .willReturn(Optional.of(sampleIsland(user)));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());
            given(territoryRepository.countByOwnerId(1L)).willReturn(3L);

            stubVaultGp(1L, 1500);
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
            UserSeasonPass activePass =
                    UserSeasonPass.builder()
                            .user(user)
                            .seasonPass(sampleSeasonPass())
                            .startedAt(LocalDateTime.now().minusDays(1))
                            .expiresAt(LocalDateTime.now().plusDays(30))
                            .build();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(300, 0));
            given(homeIslandRepository.findByUserId(1L))
                    .willReturn(Optional.of(sampleIsland(user)));
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.of(activePass));
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

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
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

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
            given(territoryRepository.countByOwnerId(1L)).willReturn(5L);

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
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/1.png");
        }

        @Test
        @DisplayName("user_profiles 없으면 profileImageUrl = null")
        void getUserProfile_noProfileImage_returnsNull() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.profileImageUrl()).isNull();
        }

        @Test
        @DisplayName("trophyPoints가 user_trophies.score에서 반환")
        void getUserProfile_trophyPointsReturned() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userTrophyRepository.findById(1L))
                    .willReturn(Optional.of(sampleUserTrophy(user)));
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.trophyPoints()).isEqualTo(3850);
        }

        @Test
        @DisplayName("트로피 기록 없으면 trophyPoints = 0")
        void getUserProfile_noTrophy_returns0() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

            UserProfileResponse response = userService.getUserProfile(1L);

            assertThat(response.trophyPoints()).isEqualTo(0);
        }

        @Test
        @DisplayName("level이 home_islands.level에서 반환")
        void getUserProfile_levelReturned() {
            User user = sampleUser();
            HomeIsland island = HomeIsland.builder().user(user).build();
            ReflectionTestUtils.setField(island, "level", 5);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

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

    // ─── getNotificationSetting() ─────────────────────────────────────────────

    @Nested
    @DisplayName("getNotificationSetting()")
    class GetNotificationSetting {

        @Test
        @DisplayName("정상 조회 시 NotificationSettingResponse 반환")
        void getNotificationSetting_success() {
            User user = sampleUser();
            given(notificationSettingRepository.findById(1L))
                    .willReturn(Optional.of(sampleNotificationSetting(user)));

            NotificationSettingResponse response = userService.getNotificationSetting(1L);

            assertThat(response.isOutbidEnabled()).isTrue();
            assertThat(response.isAuctionStartEnabled()).isTrue();
            assertThat(response.isMarketingEnabled()).isFalse();
        }

        @Test
        @DisplayName("알림 설정이 없으면 NOTIFICATION_NOT_FOUND 예외")
        void getNotificationSetting_notFound() {
            given(notificationSettingRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getNotificationSetting(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    // ─── updateNotificationSetting() ──────────────────────────────────────────

    @Nested
    @DisplayName("updateNotificationSetting()")
    class UpdateNotificationSetting {

        @Test
        @DisplayName("전체 필드 업데이트 시 변경된 값 반환")
        void updateNotificationSetting_allFields() {
            User user = sampleUser();
            NotificationSetting setting = sampleNotificationSetting(user);
            given(notificationSettingRepository.findById(1L)).willReturn(Optional.of(setting));
            given(notificationSettingRepository.save(any(NotificationSetting.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            UpdateNotificationSettingRequest request =
                    new UpdateNotificationSettingRequest(false, false, true);

            NotificationSettingResponse response =
                    userService.updateNotificationSetting(1L, request);

            assertThat(response.isOutbidEnabled()).isFalse();
            assertThat(response.isAuctionStartEnabled()).isFalse();
            assertThat(response.isMarketingEnabled()).isTrue();
        }

        @Test
        @DisplayName("null 필드는 기존 값 유지 (Partial Update)")
        void updateNotificationSetting_partialUpdate() {
            User user = sampleUser();
            NotificationSetting setting = sampleNotificationSetting(user);
            // 기본값: isOutbidEnabled=true, isAuctionStartEnabled=true, isMarketingEnabled=false
            given(notificationSettingRepository.findById(1L)).willReturn(Optional.of(setting));
            given(notificationSettingRepository.save(any(NotificationSetting.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            UpdateNotificationSettingRequest request =
                    new UpdateNotificationSettingRequest(false, null, null);
            // isAuctionStartEnabled, isMarketingEnabled 는 null → 변경 없음

            NotificationSettingResponse response =
                    userService.updateNotificationSetting(1L, request);

            assertThat(response.isOutbidEnabled()).isFalse();
            assertThat(response.isAuctionStartEnabled()).isTrue(); // 기존 유지
            assertThat(response.isMarketingEnabled()).isFalse(); // 기존 유지
        }

        @Test
        @DisplayName("알림 설정이 없으면 NOTIFICATION_NOT_FOUND 예외")
        void updateNotificationSetting_notFound() {
            given(notificationSettingRepository.findById(99L)).willReturn(Optional.empty());

            UpdateNotificationSettingRequest request =
                    new UpdateNotificationSettingRequest(null, null, null);

            assertThatThrownBy(() -> userService.updateNotificationSetting(99L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("save 호출 여부 검증")
        void updateNotificationSetting_callsSave() {
            User user = sampleUser();
            NotificationSetting setting = sampleNotificationSetting(user);
            given(notificationSettingRepository.findById(1L)).willReturn(Optional.of(setting));
            given(notificationSettingRepository.save(any(NotificationSetting.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            UpdateNotificationSettingRequest request =
                    new UpdateNotificationSettingRequest(null, null, true);

            userService.updateNotificationSetting(1L, request);

            then(notificationSettingRepository).should().save(setting);
        }
    }

    // ─── deleteMe() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteMe()")
    class DeleteMe {

        @Test
        @DisplayName("존재하지 않는 userId 시 USER_NOT_FOUND 예외")
        void deleteMe_userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteMe(99L, "any", null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 INVALID_PASSWORD 예외 — passwordEncoder.matches() 사용 전제")
        void deleteMe_wrongPassword_throwsInvalidPassword() {
            User user = sampleUser(); // passwordHash = "encoded"
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPw", "encoded")).willReturn(false);

            assertThatThrownBy(() -> userService.deleteMe(1L, "wrongPw", null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PASSWORD);
        }

        @Test
        @DisplayName("정상 탈퇴 시 소프트 삭제 — status=WITHDRAWN, userRepository.delete() 미호출")
        void deleteMe_success_softDelete() {
            User user = sampleUser(); // passwordHash = "encoded"
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("rawPw", "encoded")).willReturn(true);

            userService.deleteMe(1L, "rawPw", null);

            // 소프트 삭제 검증: 하드 삭제 금지, status=WITHDRAWN 이어야 함
            then(userRepository).should(never()).delete(user);
            assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            then(refreshTokenService).should().delete(1L);
        }
    }

    // ─── getMyWallet() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyWallet()")
    class GetMyWallet {

        @Test
        @DisplayName("정상 조회 시 MyWalletResponse 반환")
        void getMyWallet_success() {
            User user = sampleUser();

            given(walletClient.getWallet(1L)).willReturn(new WalletSnapshot(300, 0));

            stubVaultGp(1L, 1500);
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

        private Territory sampleTerritory() {
            TerritoryGrade grade = mock(TerritoryGrade.class);
            given(grade.getGrade()).willReturn("A");

            Continent continent = mock(Continent.class);
            given(continent.getDisplayName()).willReturn("아시아");

            Territory territory = mock(Territory.class);
            given(territory.getId()).willReturn(10L);
            given(territory.getCoordX()).willReturn(3);
            given(territory.getCoordY()).willReturn(7);
            given(territory.getGrade()).willReturn(grade);
            given(territory.getContinent()).willReturn(continent);
            return territory;
        }

        @Test
        @DisplayName("영토가 있으면 totalCount와 territoryInfos 반환")
        void getMyTerritories_success() {
            Territory territory = sampleTerritory();
            Page<Territory> page = new PageImpl<>(List.of(territory));
            PageRequest pageable = PageRequest.of(0, 10);

            given(territoryRepository.findAllByUserId(1L, pageable)).willReturn(page);

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
            Page<Territory> emptyPage = new PageImpl<>(Collections.emptyList());
            PageRequest pageable = PageRequest.of(0, 10);

            given(territoryRepository.findAllByUserId(1L, pageable)).willReturn(emptyPage);

            MyTerritoryResponse response = userService.getMyTerritories(1L, pageable);

            assertThat(response.totalCount()).isEqualTo(0);
            assertThat(response.territories()).isEmpty();
        }
    }

    // ─── changeUserNickname() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("changeUserNickname()")
    class ChangeUserNickname {

        @Test
        @DisplayName("정상 변경 시 ChangeNicknameResponse 반환")
        void changeUserNickname_success() {
            User user = sampleUser(); // nickname = "픽셀전사"
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("새닉네임")).willReturn(false);
            given(userRepository.save(user)).willReturn(user);

            ChangeNicknameResponse response = userService.changeUserNickname(1L, "새닉네임");

            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.nickname()).isEqualTo("새닉네임");
            assertThat(response.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("중복 닉네임이면 DUPLICATE_NICKNAME 예외")
        void changeUserNickname_duplicate() {
            User user = sampleUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("중복닉네임")).willReturn(true);

            assertThatThrownBy(() -> userService.changeUserNickname(1L, "중복닉네임"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        }

        @Test
        @DisplayName("존재하지 않는 userId 시 USER_NOT_FOUND 예외")
        void changeUserNickname_userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeUserNickname(99L, "닉네임"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ─── changeUserPassword() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("changeUserPassword()")
    class ChangeUserPassword {

        @Test
        @DisplayName("정상 변경 시 passwordEncoder.encode 호출 후 save")
        void changeUserPassword_success() {
            User user = sampleUser(); // passwordHash = "encoded"
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("curPw", "encoded")).willReturn(true);
            given(passwordEncoder.encode("newPw")).willReturn("newEncoded");

            userService.changeUserPassword(1L, "curPw", "newPw");

            assertThat(user.getPasswordHash()).isEqualTo("newEncoded");
            then(userRepository).should().save(user);
        }

        @Test
        @DisplayName("존재하지 않는 userId 시 USER_NOT_FOUND 예외")
        void changeUserPassword_userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeUserPassword(99L, "curPw", "newPw"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
