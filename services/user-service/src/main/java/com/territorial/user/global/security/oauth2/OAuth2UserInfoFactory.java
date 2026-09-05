package com.territorial.user.global.security.oauth2;

import com.territorial.user.global.security.oauth2.provider.GoogleOAuth2UserInfo;
import com.territorial.user.global.security.oauth2.provider.KakaoOAuth2UserInfo;
import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth2 제공자: " + registrationId);
        };
    }
}
