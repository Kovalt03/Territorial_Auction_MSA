package com.territorial.season.internal;

import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminCreateSeasonRequest;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminSeasonPassView;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminSeasonView;
import com.territorial.season.internal.dto.AdminSeasonInternalDtos.AdminUpdateSeasonPassRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** admin(모놀리식) 시즌·시즌패스 관리 위임 계약. 감사 로그·권한 검증은 모놀리식 admin 레이어가 담당한다. */
@RestController
@RequestMapping("/internal/admin/seasons")
@RequiredArgsConstructor
public class AdminSeasonInternalController {

    private final AdminSeasonInternalService adminSeasonInternalService;

    @GetMapping
    public ResponseEntity<List<AdminSeasonView>> getSeasons() {
        return ResponseEntity.ok(adminSeasonInternalService.getSeasons());
    }

    @PostMapping
    public ResponseEntity<AdminSeasonView> createSeason(
            @RequestBody AdminCreateSeasonRequest request) {
        return ResponseEntity.ok(adminSeasonInternalService.createSeason(request));
    }

    @PostMapping("/{seasonId}/end")
    public ResponseEntity<AdminSeasonView> endSeason(@PathVariable Long seasonId) {
        return ResponseEntity.ok(adminSeasonInternalService.endSeason(seasonId));
    }

    @GetMapping("/passes")
    public ResponseEntity<List<AdminSeasonPassView>> getSeasonPasses() {
        return ResponseEntity.ok(adminSeasonInternalService.getSeasonPasses());
    }

    @PatchMapping("/passes/{seasonPassId}")
    public ResponseEntity<AdminSeasonPassView> updateSeasonPass(
            @PathVariable Long seasonPassId, @RequestBody AdminUpdateSeasonPassRequest request) {
        return ResponseEntity.ok(
                adminSeasonInternalService.updateSeasonPass(seasonPassId, request));
    }
}
