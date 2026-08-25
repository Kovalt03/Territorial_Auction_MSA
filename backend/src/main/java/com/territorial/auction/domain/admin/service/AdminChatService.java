package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminChatMessageListResponse;
import com.territorial.auction.domain.admin.dto.AdminChatMessageResponse;
import com.territorial.auction.domain.admin.dto.AdminChatRoomResponse;
import com.territorial.auction.domain.social.entity.ChatMessage;
import com.territorial.auction.domain.social.repository.ChatMessageRepository;
import com.territorial.auction.domain.social.repository.ChatRoomRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final AdminAuditLogger adminAuditLogger;

    public List<AdminChatRoomResponse> getRooms() {
        return chatRoomRepository.findAll().stream().map(AdminChatRoomResponse::from).toList();
    }

    public AdminChatMessageListResponse getMessages(
            Long roomId, String keyword, Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : "";
        Page<ChatMessage> page = chatMessageRepository.searchForAdmin(roomId, kw, pageable);
        List<AdminChatMessageResponse> messages =
                page.getContent().stream().map(AdminChatMessageResponse::from).toList();
        return new AdminChatMessageListResponse(
                page.getTotalElements(), page.getNumber(), page.getSize(), messages);
    }

    @Transactional
    public void deleteMessage(Long adminUserId, Long messageId) {
        ChatMessage message =
                chatMessageRepository
                        .findById(messageId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        // 삭제 전 스냅샷을 감사 로그에 남긴다(사후 확인용).
        adminAuditLogger.record(
                adminUserId,
                "CHAT_MESSAGE_DELETE",
                "CHAT_MESSAGE",
                messageId,
                Map.of(
                        "senderId", message.getSender().getId(),
                        "roomId", message.getRoom().getId(),
                        "content", message.getContent()));
        chatMessageRepository.delete(message);
    }
}
