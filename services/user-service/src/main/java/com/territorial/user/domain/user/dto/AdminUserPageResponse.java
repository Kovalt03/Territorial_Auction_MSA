package com.territorial.user.domain.user.dto;

import com.territorial.user.domain.user.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;

// 관리자 유저 검색 페이지 응답(admin-service 위임 소비). 정렬은 서버 기본값(가입 최신순).
public record AdminUserPageResponse(
        List<AdminUserView> content, long totalElements, int page, int size) {

    public static AdminUserPageResponse from(Page<User> page) {
        return new AdminUserPageResponse(
                page.getContent().stream().map(AdminUserView::from).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
