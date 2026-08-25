package com.territorial.auction.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 관리자 접근 보안 설정. ip-allowlist가 비어 있으면(미설정) 모든 IP 허용(개발 편의).
@ConfigurationProperties(prefix = "admin")
public record AdminSecurityProperties(List<String> ipAllowlist) {

    public boolean isIpAllowed(String ip) {
        if (ipAllowlist == null) {
            return true;
        }
        List<String> configured =
                ipAllowlist.stream().filter(s -> s != null && !s.isBlank()).toList();
        return configured.isEmpty() || configured.contains(ip);
    }
}
