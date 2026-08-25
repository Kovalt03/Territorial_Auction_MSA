package com.territorial.auction.domain.social.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks private ChatService chatService;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private GuildMemberRepository guildMemberRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private User sender;
    private ChatRoom globalRoom;
    private ChatRoom guildRoom;
    private ChatMessage chatMessage;

    @BeforeEach
    void setUp() {
        sender =
                User.builder()
                        .username("user1")
                        .email("user1@test.com")
                        .passwordHash("hashed")
                        .nickname("픽셀전사")
                        .build();
        ReflectionTestUtils.setField(sender, "id", 1L);

        globalRoom = ChatRoom.builder().type(ChatRoomType.WORLD).targetId(null).build();
        ReflectionTestUtils.setField(globalRoom, "id", 1L);

        guildRoom = ChatRoom.builder().type(ChatRoomType.GUILD).targetId(42L).build();
        ReflectionTestUtils.setField(guildRoom, "id", 2L);

        chatMessage =
                ChatMessage.builder()
                        .room(globalRoom)
                        .sender(sender)
                        .content("안녕하세요!")
                        .sentAt(LocalDateTime.of(2026, 5, 8, 12, 0))
                        .build();
        ReflectionTestUtils.setField(chatMessage, "id", 100L);
    }

    @Nested
    @DisplayName("sendMessage()")
    class SendMessage {

        @Test
        @DisplayName("WORLD 채팅방 — 메시지 저장 + /sub/chat/room_world 브로드캐스트")
        void sendMessage_world_success() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD))
                    .willReturn(Optional.of(globalRoom));
            given(userRepository.findById(1L)).willReturn(Optional.of(sender));
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(chatMessage);

            ChatMessageResponse response =
                    chatService.sendMessage(1L, "room_world", new SendChatMessageRequest("안녕하세요!"));

            assertThat(response.roomId()).isEqualTo("room_world");
            assertThat(response.senderNickname()).isEqualTo("픽셀전사");
            assertThat(response.content()).isEqualTo("안녕하세요!");
            then(messagingTemplate)
                    .should()
                    .convertAndSend(eq("/sub/chat/room_world"), any(ChatMessageResponse.class));
        }

        @Test
        @DisplayName("GUILD 채팅방, 멤버인 유저 — 저장 + 브로드캐스트")
        void sendMessage_guild_success() {
            given(chatRoomRepository.findByTypeAndTargetId(ChatRoomType.GUILD, 42L))
                    .willReturn(Optional.of(guildRoom));
            given(
                            guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                                    1L, 42L, GuildMember.Status.ACTIVE))
                    .willReturn(true);
            given(userRepository.findById(1L)).willReturn(Optional.of(sender));

            ChatMessage guildMessage =
                    ChatMessage.builder()
                            .room(guildRoom)
                            .sender(sender)
                            .content("길드 채팅!")
                            .sentAt(LocalDateTime.now())
                            .build();
            ReflectionTestUtils.setField(guildMessage, "id", 101L);
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(guildMessage);

            ChatMessageResponse response =
                    chatService.sendMessage(
                            1L, "room_guild_42", new SendChatMessageRequest("길드 채팅!"));

            assertThat(response.roomId()).isEqualTo("room_guild_42");
            then(messagingTemplate)
                    .should()
                    .convertAndSend(eq("/sub/chat/room_guild_42"), any(ChatMessageResponse.class));
        }

        @Test
        @DisplayName("GUILD 채팅방, 비멤버 유저 → CHAT_ACCESS_DENIED")
        void sendMessage_guild_notMember() {
            given(chatRoomRepository.findByTypeAndTargetId(ChatRoomType.GUILD, 42L))
                    .willReturn(Optional.of(guildRoom));
            given(
                            guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                                    1L, 42L, GuildMember.Status.ACTIVE))
                    .willReturn(false);

            assertThatThrownBy(
                            () ->
                                    chatService.sendMessage(
                                            1L, "room_guild_42", new SendChatMessageRequest("채팅")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("존재하지 않는 roomId → CHAT_ROOM_NOT_FOUND")
        void sendMessage_roomNotFound() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD)).willReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    chatService.sendMessage(
                                            1L, "room_world", new SendChatMessageRequest("채팅")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMessageHistory()")
    class GetMessageHistory {

        @Test
        @DisplayName("beforeId 없음 → 최신 메시지 반환")
        void getMessageHistory_noBeforeId() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD))
                    .willReturn(Optional.of(globalRoom));
            given(chatMessageRepository.findByRoom_IdOrderByIdDesc(eq(1L), any(Pageable.class)))
                    .willReturn(List.of(chatMessage));

            ChatHistoryResponse response =
                    chatService.getMessageHistory(1L, "room_world", null, 30);

            assertThat(response.messages()).hasSize(1);
            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("beforeId 있음 → 해당 ID 이전 메시지 반환")
        void getMessageHistory_withBeforeId() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD))
                    .willReturn(Optional.of(globalRoom));
            given(
                            chatMessageRepository.findByRoom_IdAndIdLessThanOrderByIdDesc(
                                    eq(1L), eq(200L), any(Pageable.class)))
                    .willReturn(List.of(chatMessage));

            ChatHistoryResponse response =
                    chatService.getMessageHistory(1L, "room_world", 200L, 30);

            assertThat(response.messages()).hasSize(1);
            assertThat(response.messages().get(0).messageId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("반환 개수 > size → hasNext=true, size개만 반환")
        void getMessageHistory_hasNextTrue() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD))
                    .willReturn(Optional.of(globalRoom));

            List<ChatMessage> messages = makeMessages(3); // size=2, fetch size+1=3
            given(chatMessageRepository.findByRoom_IdOrderByIdDesc(eq(1L), any(Pageable.class)))
                    .willReturn(messages);

            ChatHistoryResponse response = chatService.getMessageHistory(1L, "room_world", null, 2);

            assertThat(response.hasNext()).isTrue();
            assertThat(response.messages()).hasSize(2);
        }

        @Test
        @DisplayName("반환 개수 <= size → hasNext=false")
        void getMessageHistory_hasNextFalse() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD))
                    .willReturn(Optional.of(globalRoom));
            given(chatMessageRepository.findByRoom_IdOrderByIdDesc(eq(1L), any(Pageable.class)))
                    .willReturn(List.of(chatMessage));

            ChatHistoryResponse response =
                    chatService.getMessageHistory(1L, "room_world", null, 30);

            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("GUILD 채팅방, 비멤버 유저 → CHAT_ACCESS_DENIED")
        void getMessageHistory_guild_notMember() {
            given(chatRoomRepository.findByTypeAndTargetId(ChatRoomType.GUILD, 42L))
                    .willReturn(Optional.of(guildRoom));
            given(
                            guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                                    1L, 42L, GuildMember.Status.ACTIVE))
                    .willReturn(false);

            assertThatThrownBy(() -> chatService.getMessageHistory(1L, "room_guild_42", null, 30))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("존재하지 않는 roomId → CHAT_ROOM_NOT_FOUND")
        void getMessageHistory_roomNotFound() {
            given(chatRoomRepository.findByType(ChatRoomType.WORLD)).willReturn(Optional.empty());

            assertThatThrownBy(() -> chatService.getMessageHistory(1L, "room_world", null, 30))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        private List<ChatMessage> makeMessages(int count) {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(
                            i -> {
                                ChatMessage m =
                                        ChatMessage.builder()
                                                .room(globalRoom)
                                                .sender(sender)
                                                .content("msg" + i)
                                                .sentAt(LocalDateTime.now())
                                                .build();
                                ReflectionTestUtils.setField(m, "id", (long) (100 + i));
                                return m;
                            })
                    .toList();
        }
    }
}
