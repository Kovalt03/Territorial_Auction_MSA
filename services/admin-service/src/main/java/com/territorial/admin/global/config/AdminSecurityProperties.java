package com.territorial.admin.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 관리자 접근 IP 허용목록(내부망 운영). 비어 있으면 전체 허용(로컬 개발). */
@ConfigurationProperties(prefix = "admin-security")
public record AdminSecurityProperties(List<String> allowedIps) {

    public boolean isIpAllowed(String ip) {
        if (allowedIps == null) {
            return true;
        }
        List<String> configured =
                allowedIps.stream().filter(s -> s != null && !s.isBlank()).toList();
        return configured.isEmpty() || configured.contains(ip);
    }
}
