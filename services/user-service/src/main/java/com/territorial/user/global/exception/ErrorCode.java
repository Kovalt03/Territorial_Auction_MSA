package com.territorial.user.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INSUFFICIENT_AP(HttpStatus.CONFLICT, "AP 잔액이 부족합니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 사용자명입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 설정을 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "탈퇴한 사용자입니다."),
    SUSPENDED_USER(HttpStatus.FORBIDDEN, "정지된 사용자입니다."),
    INVALID_PAYMENT(HttpStatus.BAD_REQUEST, "결제 정보가 올바르지 않습니다."),
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "이미 처리된 주문입니다."),
    INVALID_WALLET_AMOUNT(HttpStatus.BAD_REQUEST, "지갑 명령 금액은 양수여야 합니다."),
    WALLET_COMMAND_CONFLICT(HttpStatus.CONFLICT, "동일한 지갑 명령 키에 다른 요청이 전달되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
