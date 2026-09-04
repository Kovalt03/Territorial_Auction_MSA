package com.territorial.ranking;

import com.territorial.auction.global.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// common(com.territorial.auction.*)의 전역 빈은 패키지가 달라 자동 스캔되지 않으므로 명시 임포트.
@EnableJpaAuditing
@EnableScheduling
@EnableCaching
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class RankingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RankingServiceApplication.class, args);
    }
}
