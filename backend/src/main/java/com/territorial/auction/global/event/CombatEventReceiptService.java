package com.territorial.auction.global.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CombatEventReceiptService {

    private final CombatEventReceiptRepository repository;

    @Transactional
    public void processOnce(String receiptKey, Runnable action) {
        if (repository.existsById(receiptKey)) {
            return;
        }
        action.run();
        repository.save(new CombatEventReceipt(receiptKey));
    }
}
