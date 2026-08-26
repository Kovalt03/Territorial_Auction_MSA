package com.territorial.auction.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 각 서비스의 도메인 ErrorCode enum이 구현하는 계약. 공유 CustomException·GlobalExceptionHandler는 이 인터페이스로만 동작해 서비스별
 * 코드와 분리된다.
 */
public interface ErrorCodeType {

    HttpStatus getHttpStatus();

    String getMessage();
}
