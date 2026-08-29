package com.territorial.auction.global.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {
    /** SpEL 표현식으로 락 키를 동적으로 지정. 예: "'lock:auction:' + #auctionId" */
    String key();

    /** 락 획득 대기 시간 */
    long waitTime() default 5L;

    /** 락 보유 최대 시간 */
    long leaseTime() default 10L;

    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
