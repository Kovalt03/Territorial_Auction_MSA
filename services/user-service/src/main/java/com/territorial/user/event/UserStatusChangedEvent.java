package com.territorial.user.event;

/** 상태 변경(탈퇴·정지·복구) 전파용. 모놀리식 프로젝션 status와 admin 목록/표시에 반영. */
public record UserStatusChangedEvent(Long userId, String status) {}
