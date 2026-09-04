package com.territorial.user.domain.user.dto;

import com.territorial.user.domain.user.entity.User;

// 표시용 닉네임 배치 조회 응답(ranking-service 등 위임 소비자용).
public record UserNicknameResponse(Long userId, String nickname) {
    public static UserNicknameResponse from(User user) {
        return new UserNicknameResponse(user.getId(), user.getNickname());
    }
}
