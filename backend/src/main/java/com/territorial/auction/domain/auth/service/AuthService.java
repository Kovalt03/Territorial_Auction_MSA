package com.territorial.auction.domain.auth.service;

import com.territorial.auction.domain.auth.dto.*;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.entity.IslandGrade;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.building.repository.IslandGradeRepository;
import com.territorial.auction.domain.user.entity.*;
import com.territorial.auction.domain.user.repository.NotificationSettingRepository;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final WalletRepository walletRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final IslandGradeRepository islandGradeRepository;
    private final UserProfileRepository userProfileRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 중복 검사
        if (userRepository.existsByUsername(request.username()))
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        if (userRepository.existsByEmail(request.email()))
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        if (userRepository.existsByNickname(request.nickname()))
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);

        // User 저장
        User user =
                User.builder()
                        .username(request.username())
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .nickname(request.nickname())
                        .build();
        userRepository.save(user);

        // Wallet 생성
        Wallet wallet = Wallet.builder().user(user).build();
        walletRepository.save(wallet);

        // GlobalVault 생성
        GlobalVault globalVault = GlobalVault.builder().user(user).build();
        globalVaultRepository.save(globalVault);

        // Notification 생성
        NotificationSetting notificationSetting = NotificationSetting.builder().user(user).build();
        notificationSettingRepository.save(notificationSetting);

        // HomeIsland 생성 + 기본 성 배치
        IslandGrade dGrade = islandGradeRepository.findByName("D").orElse(null);
        HomeIsland homeIsland = HomeIsland.builder().user(user).islandGrade(dGrade).build();
        homeIslandRepository.save(homeIsland);
        placeDefaultCastle(homeIsland);

        // UserProfile 생성
        UserProfile userProfile = UserProfile.builder().user(user).build();
        userProfileRepository.save(userProfile);

        return SignupResponse.from(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        // email로 조회
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // 유저 상태 검증
        if (user.getStatus() == UserStatus.WITHDRAWN)
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        if (user.getStatus() == UserStatus.SUSPENDED)
            throw new CustomException(ErrorCode.SUSPENDED_USER);

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);

        // 관리자 계정은 강화된 관리자 로그인(/api/v1/admin/auth/login)만 허용
        if (user.isAdmin()) throw new CustomException(ErrorCode.ADMIN_LOGIN_REQUIRED);

        // Access/Refresh 토큰 발급 (role 포함)
        String accessToken =
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // Redis 저장
        refreshTokenService.save(user.getId(), refreshToken);

        return new TokenPair(accessToken, refreshToken);
    }

    public TokenPair refresh(String refreshToken) {
        // refreshToken 파싱
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // Redis 검증
        if (!refreshTokenService.isValid(userId, refreshToken))
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);

        // role 유지를 위해 유저 조회 후 토큰 재발급
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        // Redis 갱신
        refreshTokenService.save(userId, newRefreshToken);

        return new TokenPair(accessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        // Redis에서 refreshToken 삭제
        refreshTokenService.delete(userId);
    }

    public void checkNickname(String nickname) {
        if (userRepository.existsByNickname(nickname))
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
    }

    public void checkEmail(String email) {
        if (userRepository.existsByEmail(email))
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
    }

    public void checkUsername(String username) {
        if (userRepository.existsByUsername(username))
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
    }

    private void placeDefaultCastle(HomeIsland island) {
        BuildingType castleType =
                buildingTypeRepository
                        .findByName("CASTLE")
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
        // 10×10 그리드 기준 2×2 성을 정중앙(4,4)에 배치
        int center = (island.getGridSize() / 2) - 1;
        buildingInstanceRepository.save(
                BuildingInstance.builder()
                        .island(island)
                        .buildingType(castleType)
                        .posX(center)
                        .posY(center)
                        .hp(castleType.getMaxHp())
                        .zone(1)
                        .build());
    }
}
