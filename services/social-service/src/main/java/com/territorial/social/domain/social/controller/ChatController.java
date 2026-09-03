package com.territorial.social.domain.social.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.social.domain.social.dto.ChatHistoryResponse;
import com.territorial.social.domain.social.dto.ChatMessageResponse;
import com.territorial.social.domain.social.dto.SendChatMessageRequest;
import com.territorial.social.domain.social.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String roomId,
            @RequestBody @Valid SendChatMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.sendMessage(userId, roomId, request)));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getHistory(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(chatService.getMessageHistory(userId, roomId, beforeId, size)));
    }
}
