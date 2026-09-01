package com.territorial.auction.domain.admin.client;

import com.territorial.auction.domain.admin.dto.AdminChatMessageListResponse;
import com.territorial.auction.domain.admin.dto.AdminChatRoomResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 채팅 검열을 social-service /internal/chat로 위임. 감사 로그는 모놀리식 admin이 남긴다. */
@Component
public class SocialChatClient {

    private final RestClient restClient;

    public SocialChatClient(
            RestClient.Builder builder, @Value("${social-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<AdminChatRoomResponse> getRooms() {
        return restClient
                .get()
                .uri("/internal/chat/rooms")
                .retrieve()
                .body(new ParameterizedTypeReference<List<AdminChatRoomResponse>>() {});
    }

    public AdminChatMessageListResponse getMessages(
            Long roomId, String keyword, int page, int size) {
        String uri =
                UriComponentsBuilder.fromPath("/internal/chat/messages")
                        .queryParamIfPresent("roomId", java.util.Optional.ofNullable(roomId))
                        .queryParamIfPresent("keyword", java.util.Optional.ofNullable(keyword))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build()
                        .toUriString();
        return restClient.get().uri(uri).retrieve().body(AdminChatMessageListResponse.class);
    }

    public MessageSnapshot deleteMessage(Long messageId) {
        return restClient
                .delete()
                .uri("/internal/chat/messages/{id}", messageId)
                .retrieve()
                .body(MessageSnapshot.class);
    }

    public record MessageSnapshot(Long senderId, Long roomId, String content) {}
}
