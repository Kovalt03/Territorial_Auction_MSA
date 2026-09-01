package com.territorial.social.domain.guild.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.social.domain.guild.dto.CreateGuildRequest;
import com.territorial.social.domain.guild.dto.CreateGuildResponse;
import com.territorial.social.domain.guild.dto.GuildApplicationListResponse;
import com.territorial.social.domain.guild.dto.GuildDetailResponse;
import com.territorial.social.domain.guild.dto.GuildListResponse;
import com.territorial.social.domain.guild.dto.JoinGuildRequest;
import com.territorial.social.domain.guild.dto.MyGuildResponse;
import com.territorial.social.domain.guild.dto.TransferMasterRequest;
import com.territorial.social.domain.guild.dto.UpdateGuildRequest;
import com.territorial.social.domain.guild.service.GuildService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildService guildService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateGuildResponse>> createGuild(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CreateGuildRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created", guildService.createGuild(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GuildListResponse>> getGuilds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(
                ApiResponse.ok(guildService.getGuilds(search, PageRequest.of(page, size))));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyGuildResponse>> getMyGuild(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(guildService.getMyGuild(userId)));
    }

    @GetMapping("/{guildId}")
    public ResponseEntity<ApiResponse<GuildDetailResponse>> getGuildDetail(
            @PathVariable Long guildId) {
        return ResponseEntity.ok(ApiResponse.ok(guildService.getGuildDetail(guildId)));
    }

    @PostMapping("/{guildId}/join")
    public ResponseEntity<ApiResponse<Void>> joinGuild(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long guildId,
            @RequestBody(required = false) @Valid JoinGuildRequest request) {
        guildService.joinGuild(userId, guildId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok("Accepted", null));
    }

    @PatchMapping("/{guildId}/members/{targetUserId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveApplication(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long guildId,
            @PathVariable Long targetUserId) {
        guildService.approveApplication(userId, guildId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{guildId}/applications")
    public ResponseEntity<ApiResponse<GuildApplicationListResponse>> getApplications(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long guildId) {
        return ResponseEntity.ok(ApiResponse.ok(guildService.getApplications(userId, guildId)));
    }

    @PatchMapping("/{guildId}/members/{targetUserId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectApplication(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long guildId,
            @PathVariable Long targetUserId) {
        guildService.rejectApplication(userId, guildId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{guildId}/master")
    public ResponseEntity<ApiResponse<Void>> transferMaster(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long guildId,
            @RequestBody @Valid TransferMasterRequest request) {
        guildService.transferMaster(userId, guildId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{guildId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long guildId,
            @PathVariable Long targetUserId) {
        guildService.kickMember(userId, guildId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{guildId}")
    public ResponseEntity<ApiResponse<Void>> updateGuild(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long guildId,
            @RequestBody @Valid UpdateGuildRequest request) {
        guildService.updateGuild(userId, guildId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{guildId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveGuild(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long guildId) {
        guildService.leaveGuild(userId, guildId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{guildId}/join")
    public ResponseEntity<ApiResponse<Void>> cancelJoinApplication(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long guildId) {
        guildService.cancelJoinApplication(userId, guildId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
