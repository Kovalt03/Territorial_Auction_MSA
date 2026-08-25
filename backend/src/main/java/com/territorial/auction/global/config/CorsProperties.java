package com.territorial.auction.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public List<String> origins() {
        return allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .filter(origin -> origin != null && !origin.isBlank())
                        .toList();
    }
}
