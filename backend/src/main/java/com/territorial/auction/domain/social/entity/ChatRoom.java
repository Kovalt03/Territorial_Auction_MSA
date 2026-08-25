package com.territorial.auction.domain.social.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRoomType type;

    private Long targetId; // 길드 ID (GUILD) 또는 대륙 ID (CONTINENT), WORLD는 null

    @Builder
    public ChatRoom(ChatRoomType type, Long targetId) {
        this.type = type;
        this.targetId = targetId;
    }

    public String toRoomId() {
        return switch (type) {
            case WORLD -> "room_world";
            case GUILD -> "room_guild_" + targetId;
            case CONTINENT -> "room_continent_" + targetId;
        };
    }

    public enum ChatRoomType {
        WORLD,
        GUILD,
        CONTINENT
    }
}
