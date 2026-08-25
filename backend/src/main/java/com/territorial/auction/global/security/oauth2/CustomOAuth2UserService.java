package com.territorial.auction.global.security.oauth2;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.entity.IslandGrade;
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
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final IslandGradeRepository islandGradeRepository;
    private final UserProfileRepository userProfileRepository;
    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo =
                OAuth2UserInfoFactory.of(registrationId, oAuth2User.getAttributes());

        User user = saveOrUpdate(userInfo, registrationId);

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    protected User saveOrUpdate(OAuth2UserInfo userInfo, String provider) {
        // username = "provider:providerId" (e.g. "google:1234567890")
        String username = provider + ":" + userInfo.getId();
        String email = userInfo.getEmail() != null ? userInfo.getEmail() : username + "@oauth";

        return userRepository
                .findByUsername(username)
                .orElseGet(() -> createNewOAuth2User(username, email, userInfo.getName()));
    }

    private User createNewOAuth2User(String username, String email, String name) {
        User user =
                userRepository.save(
                        User.builder()
                                .username(username)
                                .email(email)
                                .passwordHash("")
                                .nickname(generateNickname(name))
                                .build());
        walletRepository.save(Wallet.builder().user(user).build());
        notificationSettingRepository.save(NotificationSetting.builder().user(user).build());
        IslandGrade dGrade = islandGradeRepository.findByName("D").orElse(null);
        HomeIsland homeIsland =
                homeIslandRepository.save(
                        HomeIsland.builder().user(user).islandGrade(dGrade).build());
        placeDefaultCastle(homeIsland);
        userProfileRepository.save(UserProfile.builder().user(user).build());
        return user;
    }

    private void placeDefaultCastle(HomeIsland island) {
        BuildingType castleType =
                buildingTypeRepository
                        .findByName("CASTLE")
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
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

    private String generateNickname(String name) {
        return name + "_" + System.currentTimeMillis() % 10000;
    }
}
