package com.territorial.auction.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {

    public String callbackUrl() {
        return normalizedBaseUrl() + "/oauth2/callback";
    }

    public String oauthFailureUrl() {
        return normalizedBaseUrl() + "/login?error=oauth2";
    }

    private String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
