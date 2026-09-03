package com.territorial.social.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 표시용 유저 프로젝션 — user-service의 user.created/updated 이벤트로 채운다. 채팅·길드 닉네임 해소용. */
@Entity
@Table(name = "user_display")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDisplay {

    @Id private Long userId;

    @Column(nullable = false, length = 30)
    private String nickname;

    public UserDisplay(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
