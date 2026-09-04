package com.territorial.admin.domain.admin.service;

import com.territorial.admin.domain.admin.entity.AdminAccount;
import com.territorial.admin.domain.admin.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// admin.seed.email / password 가 설정된 경우에만 관리자 계정(admin_accounts)을 부트스트랩한다.
// 미설정(운영 기본) 시 아무 동작도 하지 않는다. 기존 운영 관리자는 users→admin_accounts 일회성 이관(운영 스텝)으로 옮긴다.
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final AdminAccountRepository adminAccountRepository;
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
        if (adminAccountRepository.findByEmail(seedEmail).isPresent()) {
            return;
        }
        AdminAccount admin =
                AdminAccount.builder()
                        .email(seedEmail)
                        .passwordHash(passwordEncoder.encode(seedPassword))
                        .role(AdminAccount.Role.SUPER_ADMIN)
                        .build();
        adminAccountRepository.save(admin);
        log.info("관리자 시드 계정 생성. email={}", seedEmail);
    }
}
