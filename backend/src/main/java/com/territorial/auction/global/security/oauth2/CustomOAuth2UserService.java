package com.territorial.auction.global.security.oauth2;

import com.territorial.auction.domain.building.service.UserBootstrapService;
import com.territorial.auction.domain.user.client.OAuthProvisionResult;
import com.territorial.auction.domain.user.client.UserProvisioningClient;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
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
    private final UserProvisioningClient userProvisioningClient;
    private final UserBootstrapService userBootstrapService;

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
                .orElseGet(() -> provisionOAuth2User(username, email, userInfo.getName()));
    }

    private User provisionOAuth2User(String username, String email, String name) {
        // 신원(User·Wallet)은 user-service가 소유한다. 동기 프로비저닝으로 발급 ID를 받아 로컬 프로젝션을 만든다.
        OAuthProvisionResult provisioned =
                userProvisioningClient.provisionOAuth(username, email, generateNickname(name));
        userBootstrapService.bootstrap(
                provisioned.userId(),
                provisioned.username(),
                provisioned.email(),
                provisioned.nickname());
        return userRepository
                .findById(provisioned.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private String generateNickname(String name) {
        return name + "_" + System.currentTimeMillis() % 10000;
    }
}
