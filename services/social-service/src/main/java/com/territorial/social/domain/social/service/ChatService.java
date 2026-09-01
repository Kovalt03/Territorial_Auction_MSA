package com.territorial.social.domain.social.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.social.domain.guild.entity.GuildMember;
import com.territorial.social.domain.guild.repository.GuildMemberRepository;
import com.territorial.social.domain.social.dto.ChatHistoryResponse;
import com.territorial.social.domain.social.dto.ChatMessageResponse;
import com.territorial.social.domain.social.dto.SendChatMessageRequest;
import com.territorial.social.domain.social.entity.ChatMessage;
import com.territorial.social.domain.social.entity.ChatRoom;
import com.territorial.social.domain.social.entity.ChatRoom.ChatRoomType;
import com.territorial.social.domain.social.repository.ChatMessageRepository;
import com.territorial.social.domain.social.repository.ChatRoomRepository;
import com.territorial.social.domain.user.entity.UserDisplay;
import com.territorial.social.domain.user.repository.UserDisplayRepository;
import com.territorial.social.event.ChatEventPublisher;
import com.territorial.social.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final UserDisplayRepository userDisplayRepository;
    private final ChatEventPublisher chatEventPublisher;

    @Transactional
    public ChatMessageResponse sendMessage(
            Long userId, String roomId, SendChatMessageRequest request) {
        ChatRoom room = getOrCreateRoom(roomId);
        validateAccess(userId, room);
        ChatMessage saved =
                chatMessageRepository.save(
                        ChatMessage.builder()
                                .room(room)
                                .senderId(userId)
                                .content(request.content())
                                .sentAt(LocalDateTime.now())
                                .build());
        ChatMessageResponse response =
                ChatMessageResponse.from(saved, roomId, resolveNickname(userId));
        chatEventPublisher.publish(response); // 모놀리식 허브가 /sub/chat/{roomId}로 브로드캐스트
        return response;
    }

    public ChatHistoryResponse getMessageHistory(
            Long userId, String roomId, Long beforeId, int size) {
        Optional<ChatRoom> roomOpt = findRoom(roomId);
        if (roomOpt.isEmpty()) {
            return new ChatHistoryResponse(List.of(), false);
        }
        ChatRoom room = roomOpt.get();
        validateAccess(userId, room);
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<ChatMessage> messages =
                (beforeId != null)
                        ? chatMessageRepository.findByRoom_IdAndIdLessThanOrderByIdDesc(
                                room.getId(), beforeId, pageable)
                        : chatMessageRepository.findByRoom_IdOrderByIdDesc(room.getId(), pageable);
        boolean hasNext = messages.size() > size;
        List<ChatMessage> page = messages.stream().limit(size).toList();
        Map<Long, String> nicknames = resolveNicknames(page.stream().map(ChatMessage::getSenderId));
        List<ChatMessageResponse> result =
                page.stream()
                        .map(
                                m ->
                                        ChatMessageResponse.from(
                                                m,
                                                roomId,
                                                nicknames.getOrDefault(
                                                        m.getSenderId(), UNKNOWN_NICKNAME)))
                        .toList();
        return new ChatHistoryResponse(result, hasNext);
    }

    private String resolveNickname(Long userId) {
        return userDisplayRepository
                .findById(userId)
                .map(d -> d.getNickname())
                .orElse(UNKNOWN_NICKNAME);
    }

    private Map<Long, String> resolveNicknames(java.util.stream.Stream<Long> userIds) {
        List<Long> ids = userIds.distinct().toList();
        return userDisplayRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserDisplay::getUserId, UserDisplay::getNickname));
    }

    private Optional<ChatRoom> findRoom(String roomId) {
        RoomKey key = parseRoomId(roomId);
        return key.targetId() == null
                ? chatRoomRepository.findByType(key.type())
                : chatRoomRepository.findByTypeAndTargetId(key.type(), key.targetId());
    }

    private ChatRoom getOrCreateRoom(String roomId) {
        RoomKey key = parseRoomId(roomId);
        return findRoom(roomId)
                .orElseGet(
                        () ->
                                chatRoomRepository.save(
                                        ChatRoom.builder()
                                                .type(key.type())
                                                .targetId(key.targetId())
                                                .build()));
    }

    private RoomKey parseRoomId(String roomId) {
        if ("room_world".equals(roomId)) {
            return new RoomKey(ChatRoomType.WORLD, null);
        }
        if (roomId.startsWith("room_guild_")) {
            return new RoomKey(
                    ChatRoomType.GUILD, Long.parseLong(roomId.substring("room_guild_".length())));
        }
        if (roomId.startsWith("room_continent_")) {
            return new RoomKey(
                    ChatRoomType.CONTINENT,
                    Long.parseLong(roomId.substring("room_continent_".length())));
        }
        throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    private void validateAccess(Long userId, ChatRoom room) {
        if (room.getType() == ChatRoomType.GUILD) {
            boolean isMember =
                    guildMemberRepository.existsByUserIdAndGuild_IdAndStatus(
                            userId, room.getTargetId(), GuildMember.Status.ACTIVE);
            if (!isMember) {
                throw new CustomException(ErrorCode.CHAT_ACCESS_DENIED);
            }
        }
    }

    private record RoomKey(ChatRoomType type, Long targetId) {}
}
