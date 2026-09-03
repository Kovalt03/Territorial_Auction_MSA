package com.territorial.combat.domain.building.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@Order(7)
@ConditionalOnProperty(
        name = "combat.user-events.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class UserEventListenerStarter implements ApplicationRunner {

    private static final String LISTENER_ID = "combatUserEventListener";

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Override
    public void run(ApplicationArguments args) {
        MessageListenerContainer listener =
                kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
        if (listener != null && !listener.isRunning()) {
            listener.start();
        }
    }
}
