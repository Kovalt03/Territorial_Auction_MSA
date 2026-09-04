package com.territorial.admin.domain.admin.dto;

import java.time.LocalDateTime;

public record AdminChatMessageResponse(
        Long messageId,
        Long roomId,
        String roomLabel,
        Long senderId,
        String senderNickname,
        String content,
        LocalDateTime sentAt) {}
