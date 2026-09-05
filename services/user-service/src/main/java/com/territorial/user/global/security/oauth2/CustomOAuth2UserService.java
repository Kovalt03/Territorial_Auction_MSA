package com.territorial.user.global.security.oauth2;

import com.territorial.user.domain.user.dto.OAuthProvisionResult;
import com.territorial.user.domain.user.service.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * OAuth 로그인 사용자 로딩. user-service가 신원을 소유하므로 provider 정보로 로컬 프로비저닝(username=provider:providerId 기준
 * 멱등)한 뒤 발급 userId로 인증 주체를 만든다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserProvisioningService provisioningService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo =
                OAuth2UserInfoFactory.of(registrationId, oAuth2User.getAttributes());

        String username = registrationId + ":" + userInfo.getId();
        String email = userInfo.getEmail() != null ? userInfo.getEmail() : username + "@oauth";

        OAuthProvisionResult result =
                provisioningService.provisionOAuth(
                        username, email, generateNickname(userInfo.getName()));

        return new CustomOAuth2User(result.userId(), result.nickname(), oAuth2User.getAttributes());
    }

    private String generateNickname(String name) {
        return name + "_" + System.currentTimeMillis() % 10000;
    }
}
