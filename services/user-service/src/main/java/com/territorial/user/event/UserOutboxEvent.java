package com.territorial.user.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    public UserOutboxEvent(String topic, String payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }
}
