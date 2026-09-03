package com.territorial.combat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatOutboxServiceTest {

    @InjectMocks private CombatOutboxService service;
    @Mock private CombatOutboxEventRepository repository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appendSerializesPayloadAndKeepsAggregateKey() {
        service.append("SIEGE", 10L, "combat.siege.declared", new Payload(10L, 20L));

        ArgumentCaptor<CombatOutboxEvent> captor = ArgumentCaptor.forClass(CombatOutboxEvent.class);
        then(repository).should().save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("SIEGE");
        assertThat(captor.getValue().getAggregateId()).isEqualTo("10");
        assertThat(captor.getValue().getEventTopic()).isEqualTo("combat.siege.declared");
        assertThat(captor.getValue().getPayload()).contains("\"territoryId\":20");
        then(repository).should().save(any());
    }

    private record Payload(Long siegeId, Long territoryId) {}
}
