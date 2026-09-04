package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AdminUpdateAnnouncementRequest;
import com.territorial.admin.domain.admin.dto.AnnouncementResponse;
import com.territorial.admin.domain.admin.service.AnnouncementService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/announcement")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<ApiResponse<AnnouncementResponse>> getAnnouncement() {
        return ResponseEntity.ok(ApiResponse.ok(announcementService.getForAdmin()));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<AnnouncementResponse>> updateAnnouncement(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminUpdateAnnouncementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(announcementService.update(adminUserId, request)));
    }
}
