package com.territorial.user.global.security.oauth2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** OAuth 인증 주체 — user-service가 프로비저닝한 userId·nickname을 담는다. */
public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final String nickname;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(Long userId, String nickname, Map<String, Object> attributes) {
        this.userId = userId;
        this.nickname = nickname;
        this.attributes = attributes;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return nickname;
    }
}
