package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.social.entity.ChatMessage;
import java.time.LocalDateTime;

public record AdminChatMessageResponse(
        Long messageId,
        Long roomId,
        String roomLabel,
        Long senderId,
        String senderNickname,
        String content,
        LocalDateTime sentAt) {

    public static AdminChatMessageResponse from(ChatMessage m) {
        return new AdminChatMessageResponse(
                m.getId(),
                m.getRoom().getId(),
                m.getRoom().toRoomId(),
                m.getSender().getId(),
                m.getSender().getNickname(),
                m.getContent(),
                m.getSentAt());
    }
}
