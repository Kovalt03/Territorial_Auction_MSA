package com.territorial.auction.domain.social.controller;

import com.territorial.auction.domain.social.dto.ChatHistoryResponse;
import com.territorial.auction.domain.social.dto.SendChatMessageRequest;
import com.territorial.auction.domain.social.service.ChatService;
import com.territorial.auction.global.common.ApiResponse;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat/{roomId}")
    public void handleMessage(
            @DestinationVariable String roomId,
            Principal principal,
            @Payload @Valid SendChatMessageRequest request) {
        if (principal == null) {
            throw new CustomException(ErrorCode.CHAT_ACCESS_DENIED);
        }
        Long userId = Long.parseLong(principal.getName());
        chatService.sendMessage(userId, roomId, request);
    }

    @MessageExceptionHandler(CustomException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleChatException(CustomException ex) {
        return ApiResponse.error(ex.getErrorCode().getMessage());
    }

    @GetMapping("/api/v1/chat/rooms/{roomId}/messages")
    @ResponseBody
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable String roomId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(chatService.getMessageHistory(userId, roomId, before, size)));
    }
}
