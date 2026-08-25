package com.territorial.auction.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.building.repository.IslandGradeRepository;
import com.territorial.auction.domain.user.entity.NotificationSetting;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserProfile;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.NotificationSettingRepository;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @InjectMocks private CustomOAuth2UserService customOAuth2UserService;

    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private IslandGradeRepository islandGradeRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    private OAuth2UserInfo userInfo;

    @BeforeEach
    void setUp() {
        // Google OAuth2 응답을 흉내낸 최소 구현체
        userInfo =
                new OAuth2UserInfo(
                        java.util.Map.of(
                                "sub",
                                "google-uid-999",
                                "email",
                                "oauth@example.com",
                                "name",
                                "테스트유저")) {
                    @Override
                    public String getId() {
                        return "google-uid-999";
                    }

                    @Override
                    public String getName() {
                        return "테스트유저";
                    }

                    @Override
                    public String getEmail() {
                        return "oauth@example.com";
                    }
                };
    }

    // ─── saveOrUpdate() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveOrUpdate()")
    class SaveOrUpdate {

        @Test
        @DisplayName("신규 가입 시 Wallet/NotificationSetting/HomeIsland/UserProfile 각 1회 저장")
        void saveOrUpdate_newUser_createsDomainObjects() {
            // given
            given(userRepository.findByUsername("google:google-uid-999"))
                    .willReturn(Optional.empty());
            given(buildingTypeRepository.findByName("CASTLE"))
                    .willReturn(
                            Optional.of(
                                    BuildingType.builder()
                                            .name("CASTLE")
                                            .width(2)
                                            .height(2)
                                            .maxHp(1000)
                                            .baseCostGp(0)
                                            .build()));

            User savedUser =
                    User.builder()
                            .username("google:google-uid-999")
                            .email("oauth@example.com")
                            .passwordHash("")
                            .nickname("테스트유저_1234")
                            .build();
            ReflectionTestUtils.setField(savedUser, "id", 1L);
            given(userRepository.save(any(User.class))).willReturn(savedUser);
            given(homeIslandRepository.save(any(HomeIsland.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            User result = customOAuth2UserService.saveOrUpdate(userInfo, "google");

            // then
            assertThat(result.getId()).isEqualTo(1L);
            then(walletRepository).should().save(any(Wallet.class));
            then(notificationSettingRepository).should().save(any(NotificationSetting.class));
            then(homeIslandRepository).should().save(any(HomeIsland.class));
            then(userProfileRepository).should().save(any(UserProfile.class));
        }

        @Test
        @DisplayName("기존 유저 로그인 시 도메인 객체 추가 저장 없음")
        void saveOrUpdate_existingUser_doesNotCreateDomainObjects() {
            // given
            User existingUser =
                    User.builder()
                            .username("google:google-uid-999")
                            .email("oauth@example.com")
                            .passwordHash("")
                            .nickname("기존유저_5678")
                            .build();
            ReflectionTestUtils.setField(existingUser, "id", 2L);
            given(userRepository.findByUsername("google:google-uid-999"))
                    .willReturn(Optional.of(existingUser));

            // when
            User result = customOAuth2UserService.saveOrUpdate(userInfo, "google");

            // then
            assertThat(result.getId()).isEqualTo(2L);
            then(walletRepository).should(never()).save(any(Wallet.class));
            then(notificationSettingRepository)
                    .should(never())
                    .save(any(NotificationSetting.class));
            then(homeIslandRepository).should(never()).save(any(HomeIsland.class));
            then(userProfileRepository).should(never()).save(any(UserProfile.class));
        }
    }
}
