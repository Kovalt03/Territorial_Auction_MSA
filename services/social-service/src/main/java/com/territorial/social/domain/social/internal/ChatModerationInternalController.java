package com.territorial.social.domain.social.internal;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.social.domain.social.entity.ChatMessage;
import com.territorial.social.domain.social.entity.ChatRoom;
import com.territorial.social.domain.social.repository.ChatMessageRepository;
import com.territorial.social.domain.social.repository.ChatRoomRepository;
import com.territorial.social.domain.user.entity.UserDisplay;
import com.territorial.social.domain.user.repository.UserDisplayRepository;
import com.territorial.social.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 채팅 검열 계약 — 모놀리식 admin이 호출. 감사 로그는 모놀리식이 남긴다(삭제 스냅샷 반환). */
@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class ChatModerationInternalController {

    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserDisplayRepository userDisplayRepository;

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomView>> rooms() {
        return ResponseEntity.ok(
                chatRoomRepository.findAll().stream().map(RoomView::from).toList());
    }

    @GetMapping("/messages")
    public ResponseEntity<MessageListView> messages(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : "";
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> result = chatMessageRepository.searchForAdmin(roomId, kw, pageable);
        Map<Long, String> nicknames =
                userDisplayRepository
                        .findAllById(
                                result.getContent().stream()
                                        .map(ChatMessage::getSenderId)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(
                                Collectors.toMap(UserDisplay::getUserId, UserDisplay::getNickname));
        List<MessageView> messages =
                result.getContent().stream()
                        .map(
                                m ->
                                        MessageView.of(
                                                m,
                                                nicknames.getOrDefault(
                                                        m.getSenderId(), UNKNOWN_NICKNAME)))
                        .toList();
        return ResponseEntity.ok(
                new MessageListView(
                        result.getTotalElements(), result.getNumber(), result.getSize(), messages));
    }

    @DeleteMapping("/messages/{messageId}")
    @Transactional
    public ResponseEntity<MessageSnapshot> delete(@PathVariable Long messageId) {
        ChatMessage message =
                chatMessageRepository
                        .findById(messageId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        MessageSnapshot snapshot =
                new MessageSnapshot(
                        message.getSenderId(), message.getRoom().getId(), message.getContent());
        chatMessageRepository.delete(message);
        return ResponseEntity.ok(snapshot);
    }

    public record RoomView(Long roomId, String type, Long targetId, String label) {
        static RoomView from(ChatRoom r) {
            return new RoomView(r.getId(), r.getType().name(), r.getTargetId(), r.toRoomId());
        }
    }

    public record MessageView(
            Long messageId,
            Long roomId,
            String roomLabel,
            Long senderId,
            String senderNickname,
            String content,
            LocalDateTime sentAt) {
        static MessageView of(ChatMessage m, String nickname) {
            return new MessageView(
                    m.getId(),
                    m.getRoom().getId(),
                    m.getRoom().toRoomId(),
                    m.getSenderId(),
                    nickname,
                    m.getContent(),
                    m.getSentAt());
        }
    }

    public record MessageListView(
            long totalCount, int page, int size, List<MessageView> messages) {}

    public record MessageSnapshot(Long senderId, Long roomId, String content) {}
}
