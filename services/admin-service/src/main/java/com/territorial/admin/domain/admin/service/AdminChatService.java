package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.SocialChatClient;
import com.territorial.admin.domain.admin.dto.AdminChatMessageListResponse;
import com.territorial.admin.domain.admin.dto.AdminChatRoomResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 채팅 검열 — 데이터는 social-service /internal에 위임, 감사 로그만 모놀리식이 남긴다. */
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final SocialChatClient socialChatClient;
    private final AdminAuditLogger adminAuditLogger;

    public List<AdminChatRoomResponse> getRooms() {
        return socialChatClient.getRooms();
    }

    public AdminChatMessageListResponse getMessages(
            Long roomId, String keyword, Pageable pageable) {
        return socialChatClient.getMessages(
                roomId, keyword, pageable.getPageNumber(), pageable.getPageSize());
    }

    @Transactional
    public void deleteMessage(Long adminUserId, Long messageId) {
        SocialChatClient.MessageSnapshot snapshot = socialChatClient.deleteMessage(messageId);
        adminAuditLogger.record(
                adminUserId,
                "CHAT_MESSAGE_DELETE",
                "CHAT_MESSAGE",
                messageId,
                Map.of(
                        "senderId", snapshot.senderId(),
                        "roomId", snapshot.roomId(),
                        "content", snapshot.content()));
    }
}
