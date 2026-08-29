package com.territorial.user.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCreatedEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishAfterCommit(UserCreatedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
