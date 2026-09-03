package com.territorial.social.domain.social.dto;

import com.territorial.social.domain.social.entity.ChatMessage;

public record ChatMessageResponse(
        Long messageId,
        String roomId,
        Long senderId,
        String senderNickname,
        String content,
        String sentAt) {

    public static ChatMessageResponse from(
            ChatMessage message, String roomId, String senderNickname) {
        return new ChatMessageResponse(
                message.getId(),
                roomId,
                message.getSenderId(),
                senderNickname,
                message.getContent(),
                message.getSentAt().toString());
    }
}
