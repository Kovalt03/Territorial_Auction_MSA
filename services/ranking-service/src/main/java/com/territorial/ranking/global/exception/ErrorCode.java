package com.territorial.ranking.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    CONTINENT_NOT_FOUND(HttpStatus.NOT_FOUND, "대륙을 찾을 수 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
