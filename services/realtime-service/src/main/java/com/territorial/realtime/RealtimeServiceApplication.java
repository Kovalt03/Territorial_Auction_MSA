package com.territorial.realtime;

import com.territorial.auction.global.config.RedissonConfig;
import com.territorial.realtime.config.CorsProperties;
import com.territorial.realtime.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

// common(com.territorial.auction.*)의 RedissonConfig는 패키지가 달라 자동 스캔되지 않으므로 명시 임포트.
@SpringBootApplication
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class})
@Import(RedissonConfig.class)
public class RealtimeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}
