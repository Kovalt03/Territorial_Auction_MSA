package com.territorial.auction.domain.social.service;

import com.territorial.auction.domain.guild.entity.GuildMember;
import com.territorial.auction.domain.guild.repository.GuildMemberRepository;
import com.territorial.auction.domain.social.dto.ChatHistoryResponse;
import com.territorial.auction.domain.social.dto.ChatMessageResponse;
import com.territorial.auction.domain.social.dto.SendChatMessageRequest;
import com.territorial.auction.domain.social.entity.ChatMessage;
import com.territorial.auction.domain.social.entity.ChatRoom;
import com.territorial.auction.domain.social.entity.ChatRoom.ChatRoomType;
import com.territorial.auction.domain.social.repository.ChatMessageRepository;
import com.territorial.auction.domain.social.repository.ChatRoomRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatMessageResponse sendMessage(
            Long userId, String roomId, SendChatMessageRequest request) {
        ChatRoom room = findChatRoomOrThrow(roomId);
        validateAccess(userId, room);
        User sender =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatMessage message =
                ChatMessage.builder()
                        .room(room)
                        .sender(sender)
                        .content(request.content())
                        .sentAt(LocalDateTime.now())
                        .build();
        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.from(saved, roomId);
        messagingTemplate.convertAndSend("/sub/chat/" + roomId, response);
        return response;
    }

    public ChatHistoryResponse getMessageHistory(
            Long userId, String roomId, Long beforeId, int size) {
        ChatRoom room = findChatRoomOrThrow(roomId);
        validateAccess(userId, room);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<ChatMessage> messages =
                (beforeId != null)
                        ? chatMessageRepository.findByRoom_IdAndIdLessThanOrderByIdDesc(
                                room.getId(), beforeId, pageable)
                        : chatMessageRepository.findByRoom_IdOrderByIdDesc(room.getId(), pageable);

        boolean hasNext = messages.size() > size;
        List<ChatMessageResponse> result =
                messages.stream()
                        .limit(size)
                        .map(m -> ChatMessageResponse.from(m, roomId))
                        .toList();

        return new ChatHistoryResponse(result, hasNext);
    }

    private ChatRoom findChatRoomOrThrow(String roomId) {
        if ("room_world".equals(roomId)) {
            return chatRoomRepository
                    .findByType(ChatRoomType.WORLD)
                    .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        }
        if (roomId.startsWith("room_guild_")) {
            Long guildId = Long.parseLong(roomId.substring("room_guild_".length()));
            return chatRoomRepository
                    .findByTypeAndTargetId(ChatRoomType.GUILD, guildId)
                    .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        }
        if (roomId.startsWith("room_continent_")) {
            Long continentId = Long.parseLong(roomId.substring("room_continent_".length()));
            return chatRoomRepository
                    .findByTypeAndTargetId(ChatRoomType.CONTINENT, continentId)
                    .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        }
        throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    private void validateAccess(Long userId, ChatRoom room) {
        if (room.getType() == ChatRoomType.GUILD) {
            boolean isMember =
                    guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                            userId, room.getTargetId(), GuildMember.Status.ACTIVE);
            if (!isMember) {
                throw new CustomException(ErrorCode.CHAT_ACCESS_DENIED);
            }
        }
    }
}
