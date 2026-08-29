package com.territorial.auction.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 모든 서비스 공통(플랫폼) 에러 코드. 도메인 코드는 각 서비스가 ErrorCodeType을 구현해 별도 정의한다. */
@Getter
public enum CommonErrorCode implements ErrorCodeType {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "현재 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
