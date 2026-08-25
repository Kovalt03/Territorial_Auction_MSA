package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserRole;
import com.territorial.auction.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// admin.seed.email / password 가 설정된 경우에만 관리자 계정을 부트스트랩한다.
// 미설정(운영 기본) 시 아무 동작도 하지 않는다.
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:}")
    private String seedEmail;

    @Value("${admin.seed.password:}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedEmail.isBlank() || seedPassword.isBlank()) {
            return;
        }
        userRepository
                .findByEmail(seedEmail)
                .ifPresentOrElse(this::ensureAdminRole, this::createAdmin);
    }

    private void ensureAdminRole(User user) {
        if (!user.isAdmin()) {
            user.updateRole(UserRole.ADMIN);
            log.info("기존 계정을 관리자 권한으로 승격. email={}", seedEmail);
        }
    }

    private void createAdmin() {
        User admin =
                User.builder()
                        .username("admin_" + seedEmail.split("@")[0])
                        .email(seedEmail)
                        .passwordHash(passwordEncoder.encode(seedPassword))
                        .nickname("관리자")
                        .build();
        admin.updateRole(UserRole.ADMIN);
        userRepository.save(admin);
        log.info("관리자 시드 계정 생성. email={}", seedEmail);
    }
}
