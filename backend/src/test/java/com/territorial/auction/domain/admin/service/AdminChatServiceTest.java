package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.admin.client.SocialChatClient;
import com.territorial.auction.domain.admin.dto.AdminChatMessageListResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminChatServiceTest {

    @InjectMocks private AdminChatService adminChatService;
    @Mock private SocialChatClient socialChatClient;
    @Mock private AdminAuditLogger adminAuditLogger;

    @Test
    void getMessages_delegatesToSocialService() {
        AdminChatMessageListResponse list = new AdminChatMessageListResponse(0, 0, 20, List.of());
        given(socialChatClient.getMessages(1L, "kw", 0, 20)).willReturn(list);

        assertThat(adminChatService.getMessages(1L, "kw", PageRequest.of(0, 20))).isSameAs(list);
    }

    @Test
    void deleteMessage_delegatesAndLogsAudit() {
        given(socialChatClient.deleteMessage(5L))
                .willReturn(new SocialChatClient.MessageSnapshot(3L, 2L, "hi"));

        adminChatService.deleteMessage(1L, 5L);

        then(socialChatClient).should().deleteMessage(5L);
        then(adminAuditLogger)
                .should()
                .record(eq(1L), eq("CHAT_MESSAGE_DELETE"), eq("CHAT_MESSAGE"), eq(5L), anyMap());
    }
}
