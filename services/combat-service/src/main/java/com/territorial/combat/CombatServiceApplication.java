package com.territorial.combat;

import com.territorial.auction.global.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class CombatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CombatServiceApplication.class, args);
    }
}
