package com.territorial.season.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** combat-events 소비 멱등 헬퍼 — receiptKey 미기록일 때만 action 실행 후 기록. */
@Service
@RequiredArgsConstructor
public class CombatEventReceiptService {

    private final ProcessedCombatEventRepository repository;

    @Transactional
    public void processOnce(String receiptKey, Runnable action) {
        if (repository.existsById(receiptKey)) {
            return;
        }
        action.run();
        repository.save(new ProcessedCombatEvent(receiptKey));
    }
}
