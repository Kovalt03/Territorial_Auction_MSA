package com.territorial.season;

import com.territorial.auction.global.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// common(com.territorial.auction.*)의 전역 빈은 패키지가 달라 자동 스캔되지 않으므로 명시 임포트.
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class SeasonServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeasonServiceApplication.class, args);
    }
}
