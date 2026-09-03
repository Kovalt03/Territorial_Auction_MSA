package com.territorial.season.domain.season.scheduler;

import com.territorial.season.client.NotificationClient;
import com.territorial.season.domain.season.entity.UserSeasonPass;
import com.territorial.season.domain.season.repository.UserSeasonPassRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonPassScheduler {

    private static final String SEASON_PASS_EXPIRING = "SEASON_PASS_EXPIRING";

    private final UserSeasonPassRepository userSeasonPassRepository;
    private final NotificationClient notificationClient;

    @Scheduled(cron = "0 0 0 * * *")
    public void notifyExpiringPasses() {
        LocalDate today = LocalDate.now();
        notifyGroup(today, 0, "시즌 패스가 오늘 만료됩니다.");
        notifyGroup(today, 3, "시즌 패스가 3일 후 만료됩니다.");
    }

    private void notifyGroup(LocalDate today, int daysAhead, String message) {
        LocalDateTime from = today.plusDays(daysAhead).atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<UserSeasonPass> passes =
                userSeasonPassRepository.findByIsActiveTrueAndExpiresAtBetween(from, to);
        for (UserSeasonPass pass : passes) {
            try {
                notificationClient.sendNotification(
                        pass.getUserId(), SEASON_PASS_EXPIRING, message);
            } catch (Exception e) {
                log.error("시즌 패스 만료 알림 실패. userId={}", pass.getUserId(), e);
            }
        }
        log.info("시즌 패스 만료 알림 발송. daysAhead={}, count={}", daysAhead, passes.size());
    }
}
