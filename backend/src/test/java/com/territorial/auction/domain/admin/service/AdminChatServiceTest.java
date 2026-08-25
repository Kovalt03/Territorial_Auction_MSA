package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.dto.AdminChatMessageListResponse;
import com.territorial.auction.domain.social.entity.ChatMessage;
import com.territorial.auction.domain.social.entity.ChatRoom;
import com.territorial.auction.domain.social.entity.ChatRoom.ChatRoomType;
import com.territorial.auction.domain.social.repository.ChatMessageRepository;
import com.territorial.auction.domain.social.repository.ChatRoomRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminChatServiceTest {

    @InjectMocks private AdminChatService adminChatService;

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private ChatMessage message(long id) {
        ChatRoom room = ChatRoom.builder().type(ChatRoomType.WORLD).targetId(null).build();
        ReflectionTestUtils.setField(room, "id", 1L);
        User sender =
                User.builder().username("u").email("u@x").passwordHash("h").nickname("유저").build();
        ReflectionTestUtils.setField(sender, "id", 7L);
        ChatMessage m =
                ChatMessage.builder()
                        .room(room)
                        .sender(sender)
                        .content("부적절한 말")
                        .sentAt(LocalDateTime.now())
                        .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    @DisplayName("메시지 목록 조회 → 응답 매핑")
    void getMessages_maps() {
        PageRequest pageable = PageRequest.of(0, 30);
        given(chatMessageRepository.searchForAdmin(eq(1L), any(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(message(100L)), pageable, 1));

        AdminChatMessageListResponse res = adminChatService.getMessages(1L, "말", pageable);

        assertThat(res.totalCount()).isEqualTo(1);
        assertThat(res.messages().get(0).messageId()).isEqualTo(100L);
        assertThat(res.messages().get(0).senderNickname()).isEqualTo("유저");
    }

    @Test
    @DisplayName("메시지 삭제 성공 → 감사 로그 기록 후 삭제")
    void deleteMessage_success() {
        ChatMessage m = message(100L);
        given(chatMessageRepository.findById(100L)).willReturn(Optional.of(m));

        adminChatService.deleteMessage(10L, 100L);

        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("CHAT_MESSAGE_DELETE"), eq("CHAT_MESSAGE"), eq(100L), any());
        then(chatMessageRepository).should().delete(m);
    }

    @Test
    @DisplayName("없는 메시지 삭제 → CHAT_MESSAGE_NOT_FOUND, 삭제 안 함")
    void deleteMessage_notFound() {
        given(chatMessageRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminChatService.deleteMessage(10L, 9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
        then(chatMessageRepository).should(never()).delete(any());
    }
}
