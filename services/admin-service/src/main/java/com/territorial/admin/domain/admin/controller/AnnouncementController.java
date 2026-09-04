package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AnnouncementResponse;
import com.territorial.admin.domain.admin.service.AnnouncementService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 공개 엔드포인트: 모든 사용자에게 노출할 활성 공지를 조회한다.
@RestController
@RequestMapping("/api/v1/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<ApiResponse<AnnouncementResponse>> getAnnouncement() {
        return ResponseEntity.ok(ApiResponse.ok(announcementService.getPublicAnnouncement()));
    }
}
