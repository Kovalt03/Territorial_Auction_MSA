package com.territorial.combat.domain.building.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

@ExtendWith(MockitoExtension.class)
class UserEventListenerStarterTest {

    @InjectMocks private UserEventListenerStarter starter;
    @Mock private KafkaListenerEndpointRegistry registry;
    @Mock private MessageListenerContainer listener;

    @Test
    void startsListenerAfterSeedersWhenStopped() {
        given(registry.getListenerContainer("combatUserEventListener")).willReturn(listener);
        given(listener.isRunning()).willReturn(false);

        starter.run(null);

        verify(listener).start();
    }

    @Test
    void doesNotStartListenerTwice() {
        given(registry.getListenerContainer("combatUserEventListener")).willReturn(listener);
        given(listener.isRunning()).willReturn(true);

        starter.run(null);

        verify(listener, never()).start();
    }

    @Test
    void missingListenerDoesNotFailStartup() {
        given(registry.getListenerContainer("combatUserEventListener")).willReturn(null);

        starter.run(null);
    }
}
