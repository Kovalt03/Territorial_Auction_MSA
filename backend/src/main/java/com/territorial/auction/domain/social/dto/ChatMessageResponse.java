package com.territorial.auction.domain.social.dto;

import com.territorial.auction.domain.social.entity.ChatMessage;

public record ChatMessageResponse(
        Long messageId,
        String roomId,
        Long senderId,
        String senderNickname,
        String content,
        String sentAt) {

    public static ChatMessageResponse from(ChatMessage message, String roomId) {
        return new ChatMessageResponse(
                message.getId(),
                roomId,
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getContent(),
                message.getSentAt().toString());
    }
}
