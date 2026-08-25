package com.territorial.auction.global.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class DistributedLockAopTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @Mock private ProceedingJoinPoint pjp;
    @Mock private MethodSignature methodSignature;

    private DistributedLockAop aop;
    private DistributedLock annotation;

    @BeforeEach
    void setUp() throws Exception {
        aop = new DistributedLockAop(redissonClient);

        // @DistributedLock(key = "'lock:test'") 어노테이션 리플렉션으로 가져오기
        annotation = TestTarget.class.getMethod("testMethod").getAnnotation(DistributedLock.class);

        given(pjp.getSignature()).willReturn(methodSignature);
        given(methodSignature.getParameterNames()).willReturn(new String[] {});
        given(pjp.getArgs()).willReturn(new Object[] {});
        given(redissonClient.getLock(anyString())).willReturn(lock);
    }

    // 테스트용 내부 클래스
    static class TestTarget {
        @DistributedLock(key = "'lock:test'")
        public void testMethod() {}
    }

    @Nested
    @DisplayName("락 획득 성공")
    class LockAcquired {

        @BeforeEach
        void setUpLockHeld() {
            given(lock.isHeldByCurrentThread()).willReturn(true);
        }

        @Test
        @DisplayName("락 획득 → 메서드 실행 → 락 해제")
        void acquired_proceed_unlock() throws Throwable {
            given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(pjp.proceed()).willReturn("result");

            Object result = aop.around(pjp, annotation);

            assertThat(result).isEqualTo("result");
            verify(pjp).proceed();
            verify(lock).unlock();
        }

        @Test
        @DisplayName("메서드가 예외를 던져도 락은 해제된다")
        void acquired_methodThrows_lockStillReleased() throws Throwable {
            given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(pjp.proceed()).willThrow(new RuntimeException("business error"));

            assertThatThrownBy(() -> aop.around(pjp, annotation))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("business error");

            verify(lock).unlock();
        }
    }

    @Nested
    @DisplayName("락 획득 실패")
    class LockNotAcquired {

        @Test
        @DisplayName("락 획득 실패 → LOCK_ACQUISITION_FAILED 예외, 메서드 미실행")
        void notAcquired_throwsCustomException() throws Throwable {
            given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

            assertThatThrownBy(() -> aop.around(pjp, annotation))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);

            verify(pjp, never()).proceed();
            verify(lock, never()).unlock();
        }
    }
}
