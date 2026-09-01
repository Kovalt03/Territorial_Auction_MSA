package com.territorial.user.event;

public record UserCreatedEvent(Long userId, String username, String email, String nickname) {}
