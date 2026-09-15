package com.territorial.ranking.domain.ranking.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이벤트 소비 멱등 헬퍼 — eventKey 미기록일 때만 action 실행 후 기록. Kafka 재전달 시 중복 처리를 막는다. */
@Service
@RequiredArgsConstructor
public class RankingEventReceiptService {

    private final RankingProcessedEventRepository repository;

    @Transactional
    public void processOnce(String eventKey, Runnable action) {
        if (repository.existsById(eventKey)) {
            return;
        }
        action.run();
        repository.save(new RankingProcessedEvent(eventKey));
    }
}
