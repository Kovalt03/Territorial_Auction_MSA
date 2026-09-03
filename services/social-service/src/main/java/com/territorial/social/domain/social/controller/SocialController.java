package com.territorial.social.domain.social.controller;

import com.territorial.auction.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/social")
public class SocialController {

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<Void>> getFriends() {
        return ResponseEntity.status(501).body(ApiResponse.error("지원하지 않는 기능입니다."));
    }
}
