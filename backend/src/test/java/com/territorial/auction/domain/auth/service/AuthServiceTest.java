package com.territorial.auction.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.auth.dto.LoginRequest;
import com.territorial.auction.domain.auth.dto.SignupRequest;
import com.territorial.auction.domain.auth.dto.SignupResponse;
import com.territorial.auction.domain.auth.dto.TokenPair;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.building.repository.IslandGradeRepository;
import com.territorial.auction.domain.user.entity.NotificationSetting;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserProfile;
import com.territorial.auction.domain.user.entity.UserStatus;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.NotificationSettingRepository;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks private AuthService authService;

    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private IslandGradeRepository islandGradeRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;

    @Nested
    @DisplayName("signup()")
    class Signup {

        @Test
        @DisplayName("정상 가입 시 SignupResponse 반환")
        void signup_success() {
            SignupRequest request =
                    new SignupRequest("testuser", "user@example.com", "password1!", "닉네임");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("user@example.com")).willReturn(false);
            given(userRepository.existsByNickname("닉네임")).willReturn(false);
            given(passwordEncoder.encode("password1!")).willReturn("encoded");
            given(userRepository.save(any(User.class)))
                    .willAnswer(
                            inv -> {
                                User user = inv.getArgument(0);
                                ReflectionTestUtils.setField(user, "id", 1L);
                                return user;
                            });
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

            SignupResponse response = authService.signup(request);

            assertThat(response.username()).isEqualTo("testuser");
            assertThat(response.nickname()).isEqualTo("닉네임");
        }

        @Test
        @DisplayName("가입 성공 시 Wallet/GlobalVault/NotificationSetting/HomeIsland/UserProfile 자동 생성")
        void signup_createsRelatedRecords() {
            SignupRequest request =
                    new SignupRequest("testuser", "user@example.com", "password1!", "닉네임");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("user@example.com")).willReturn(false);
            given(userRepository.existsByNickname("닉네임")).willReturn(false);
            given(passwordEncoder.encode("password1!")).willReturn("encoded");
            given(userRepository.save(any(User.class)))
                    .willAnswer(
                            inv -> {
                                User user = inv.getArgument(0);
                                ReflectionTestUtils.setField(user, "id", 1L);
                                return user;
                            });
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

            authService.signup(request);

            then(walletRepository).should().save(any(Wallet.class));
            then(globalVaultRepository).should().save(any(GlobalVault.class));
            then(notificationSettingRepository).should().save(any(NotificationSetting.class));
            then(homeIslandRepository).should().save(any(HomeIsland.class));
            then(userProfileRepository).should().save(any(UserProfile.class));
        }

        @Test
        @DisplayName("username 중복 시 DUPLICATE_USERNAME 예외")
        void signup_duplicateUsername() {
            SignupRequest request =
                    new SignupRequest("testuser", "user@example.com", "password1!", "닉네임");
            given(userRepository.existsByUsername("testuser")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        }

        @Test
        @DisplayName("email 중복 시 DUPLICATE_EMAIL 예외")
        void signup_duplicateEmail() {
            SignupRequest request =
                    new SignupRequest("testuser", "user@example.com", "password1!", "닉네임");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("user@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("nickname 중복 시 DUPLICATE_NICKNAME 예외")
        void signup_duplicateNickname() {
            SignupRequest request =
                    new SignupRequest("testuser", "user@example.com", "password1!", "닉네임");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("user@example.com")).willReturn(false);
            given(userRepository.existsByNickname("닉네임")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        private User activeUser() {
            User user =
                    User.builder()
                            .username("testuser")
                            .email("user@example.com")
                            .passwordHash("encoded")
                            .nickname("닉네임")
                            .build();
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        }

        private User userWithStatus(UserStatus status) {
            User user = activeUser();
            ReflectionTestUtils.setField(user, "status", status);
            return user;
        }

        @Test
        @DisplayName("정상 로그인 시 TokenPair 반환")
        void login_success() {
            LoginRequest request = new LoginRequest("user@example.com", "password1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(activeUser()));
            given(passwordEncoder.matches("password1!", "encoded")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");

            TokenPair tokenPair = authService.login(request);

            assertThat(tokenPair.accessToken()).isEqualTo("access-token");
            assertThat(tokenPair.refreshToken()).isEqualTo("refresh-token");
            then(refreshTokenService).should().save(1L, "refresh-token");
        }

        @Test
        @DisplayName("존재하지 않는 email 시 INVALID_CREDENTIALS 예외")
        void login_userNotFound() {
            LoginRequest request = new LoginRequest("unknown@example.com", "password1!");
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("탈퇴 유저 로그인 시 WITHDRAWN_USER 예외")
        void login_withdrawnUser() {
            LoginRequest request = new LoginRequest("user@example.com", "password1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(userWithStatus(UserStatus.WITHDRAWN)));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.WITHDRAWN_USER);
        }

        @Test
        @DisplayName("정지 유저 로그인 시 SUSPENDED_USER 예외")
        void login_suspendedUser() {
            LoginRequest request = new LoginRequest("user@example.com", "password1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(userWithStatus(UserStatus.SUSPENDED)));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SUSPENDED_USER);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 INVALID_CREDENTIALS 예외")
        void login_invalidPassword() {
            LoginRequest request = new LoginRequest("user@example.com", "wrongPassword1!");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(activeUser()));
            given(passwordEncoder.matches("wrongPassword1!", "encoded")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("logout 시 refreshToken 삭제")
        void logout_deletesRefreshToken() {
            authService.logout(1L);

            then(refreshTokenService).should().delete(1L);
        }
    }

    @Nested
    @DisplayName("checkNickname()")
    class CheckNickname {

        @Test
        @DisplayName("사용 가능한 닉네임이면 예외 없음")
        void checkNickname_available() {
            given(userRepository.existsByNickname("닉네임")).willReturn(false);

            authService.checkNickname("닉네임");
        }

        @Test
        @DisplayName("중복 닉네임이면 DUPLICATE_NICKNAME 예외")
        void checkNickname_duplicate() {
            given(userRepository.existsByNickname("닉네임")).willReturn(true);

            assertThatThrownBy(() -> authService.checkNickname("닉네임"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("checkEmail()")
    class CheckEmail {

        @Test
        @DisplayName("사용 가능한 이메일이면 예외 없음")
        void checkEmail_available() {
            given(userRepository.existsByEmail("user@example.com")).willReturn(false);

            authService.checkEmail("user@example.com");
        }

        @Test
        @DisplayName("중복 이메일이면 DUPLICATE_EMAIL 예외")
        void checkEmail_duplicate() {
            given(userRepository.existsByEmail("user@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.checkEmail("user@example.com"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Nested
    @DisplayName("checkUsername()")
    class CheckUsername {

        @Test
        @DisplayName("사용 가능한 유저네임이면 예외 없음")
        void checkUsername_available() {
            given(userRepository.existsByUsername("testuser")).willReturn(false);

            authService.checkUsername("testuser");
        }

        @Test
        @DisplayName("중복 유저네임이면 DUPLICATE_USERNAME 예외")
        void checkUsername_duplicate() {
            given(userRepository.existsByUsername("testuser")).willReturn(true);

            assertThatThrownBy(() -> authService.checkUsername("testuser"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
        }
    }
}
