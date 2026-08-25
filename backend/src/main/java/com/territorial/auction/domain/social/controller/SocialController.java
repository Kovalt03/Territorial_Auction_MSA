package com.territorial.auction.domain.social.controller;

import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<Void>> getFriends() {
        return ResponseEntity.status(501).body(ApiResponse.error("지원하지 않는 기능입니다."));
    }
}
