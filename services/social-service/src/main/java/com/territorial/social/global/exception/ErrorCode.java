package com.territorial.social.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 채팅
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 채팅방에 접근 권한이 없습니다."),

    // 길드
    GUILD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 길드입니다."),
    GUILD_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 길드명입니다."),
    GUILD_FULL(HttpStatus.BAD_REQUEST, "길드 정원이 초과되었습니다."),
    ALREADY_IN_GUILD(HttpStatus.CONFLICT, "이미 길드에 소속되어 있습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 가입 신청한 길드입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "가입 신청을 찾을 수 없습니다."),
    NOT_IN_GUILD(HttpStatus.NOT_FOUND, "소속된 길드가 없습니다."),
    NOT_GUILD_MASTER(HttpStatus.FORBIDDEN, "길드장 권한이 없습니다."),
    CANNOT_KICK_MASTER(HttpStatus.FORBIDDEN, "길드장은 강제 추방할 수 없습니다."),
    CANNOT_TRANSFER_TO_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 길드장을 이전할 수 없습니다."),
    GUILD_MASTER_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "길드장은 탈퇴할 수 없습니다. 길드장을 먼저 이전하세요.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
