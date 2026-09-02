package com.territorial.combat.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CombatOutboxService {

    private final CombatOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void append(
            String aggregateType, Object aggregateId, String eventTopic, Object payload) {
        try {
            repository.save(
                    new CombatOutboxEvent(
                            aggregateType,
                            String.valueOf(aggregateId),
                            eventTopic,
                            objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("combat outbox payload 직렬화 실패", exception);
        }
    }
}
