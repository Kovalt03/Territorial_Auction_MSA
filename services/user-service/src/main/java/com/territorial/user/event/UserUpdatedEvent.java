package com.territorial.user.event;

/** 신원 변경(닉네임 등) 전파용. 모놀리식 프로젝션과 15개 도메인 표시가 최신 닉네임을 반영하게 한다. */
public record UserUpdatedEvent(Long userId, String nickname) {}
