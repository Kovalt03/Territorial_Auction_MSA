package com.territorial.user.domain.user.controller;

import com.territorial.user.domain.user.dto.AdminUserCountsResponse;
import com.territorial.user.domain.user.dto.AdminUserPageResponse;
import com.territorial.user.domain.user.dto.AdminUserView;
import com.territorial.user.domain.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway로 라우팅하지 않는 관리 콘솔용 유저 조회 계약. admin-service(UserAdminClient)가 호출한다. 신원은 user-service 소유이므로 조회
 * 전용이며, 상태 변경·프로비저닝은 {@link UserProvisioningInternalController}가 담당한다.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserAdminInternalController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<AdminUserPageResponse> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam int page,
            @RequestParam int size) {
        // 경계 넘어오며 정렬 정보는 사라지므로 서버가 결정론적 기본(가입 최신순)을 적용한다.
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(userService.searchUsersForAdmin(status, keyword, pageable));
    }

    @GetMapping("/counts")
    public ResponseEntity<AdminUserCountsResponse> counts() {
        return ResponseEntity.ok(userService.getUserCounts());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserView> get(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserView(userId));
    }

    @GetMapping("/{userId}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.existsUser(userId));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<AdminUserView>> findByIds(@RequestBody BatchUserIdsRequest request) {
        return ResponseEntity.ok(userService.findUserViews(request.userIds()));
    }

    public record BatchUserIdsRequest(List<Long> userIds) {}
}
