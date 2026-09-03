package com.territorial.season.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CONTINENT_NOT_FOUND(HttpStatus.NOT_FOUND, "대륙을 찾을 수 없습니다."),
    INSUFFICIENT_AP(HttpStatus.CONFLICT, "AP 잔액이 부족합니다."),

    SEASON_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 중인 시즌이 없습니다."),
    SEASON_BY_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "시즌을 찾을 수 없습니다."),
    SEASON_ALREADY_ACTIVE(HttpStatus.CONFLICT, "진행 중인 시즌이 있어 새 시즌을 시작할 수 없습니다."),
    SEASON_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 시즌입니다."),
    SEASON_PASS_NOT_FOUND(HttpStatus.NOT_FOUND, "시즌 패스 정보를 찾을 수 없습니다."),
    SEASON_PASS_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 이번 시즌 패스를 보유하고 있습니다."),
    SEASON_MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다."),
    MISSION_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "아직 완료하지 않은 미션입니다."),
    MISSION_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 보상을 수령한 미션입니다."),
    SEASON_REWARD_NOT_FOUND(HttpStatus.NOT_FOUND, "보상을 찾을 수 없습니다."),
    REWARD_LEVEL_NOT_REACHED(HttpStatus.BAD_REQUEST, "아직 해당 레벨에 도달하지 않았습니다."),
    REWARD_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 수령한 보상입니다."),
    REWARD_PREMIUM_REQUIRED(HttpStatus.FORBIDDEN, "프리미엄 패스가 필요한 보상입니다."),
    SEASON_LEVEL_MAX_REACHED(HttpStatus.CONFLICT, "이미 최고 레벨에 도달했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
