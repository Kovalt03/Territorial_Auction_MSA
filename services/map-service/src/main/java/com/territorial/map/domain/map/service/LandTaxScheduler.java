package com.territorial.map.domain.map.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LandTaxScheduler {

    private final LandTaxService landTaxService;

    @Scheduled(cron = "0 0 4 * * *")
    public void collectDailyLandTax() {
        landTaxService.processAllUsersTax();
    }
}
