package com.territorial.admin.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    // 관리자 인증(자체 소유)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    NOT_ADMIN_ACCOUNT(HttpStatus.FORBIDDEN, "관리자 계정이 아닙니다."),
    ADMIN_IP_NOT_ALLOWED(HttpStatus.FORBIDDEN, "허용되지 않은 IP입니다."),
    INVALID_TOTP_CODE(HttpStatus.UNAUTHORIZED, "2차 인증 코드가 올바르지 않습니다."),
    ADMIN_ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 관리자 계정입니다."),
    ADMIN_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자 계정을 찾을 수 없습니다."),

    // 관리 대상(게임 유저) — user-service 위임
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 관리 콘솔 공통
    ADMIN_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "설정을 찾을 수 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
