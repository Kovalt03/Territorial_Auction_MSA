package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AdminChatMessageListResponse;
import com.territorial.admin.domain.admin.dto.AdminChatRoomResponse;
import com.territorial.admin.domain.admin.service.AdminChatService;
import com.territorial.auction.global.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final AdminChatService adminChatService;

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<AdminChatRoomResponse>>> getRooms() {
        return ResponseEntity.ok(ApiResponse.ok(adminChatService.getRooms()));
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<AdminChatMessageListResponse>> getMessages(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 30, sort = "id", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminChatService.getMessages(roomId, keyword, pageable)));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long messageId) {
        adminChatService.deleteMessage(adminUserId, messageId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
