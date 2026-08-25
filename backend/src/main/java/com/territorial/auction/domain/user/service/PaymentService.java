package com.territorial.auction.domain.user.service;

import com.territorial.auction.domain.user.dto.ChargeApRequest;
import com.territorial.auction.domain.user.dto.ChargeApResponse;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final String ORDER_KEY_PREFIX = "payment:order:";
    private static final Duration ORDER_TTL = Duration.ofDays(1);

    private final WalletRepository walletRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public ChargeApResponse chargeAp(Long userId, ChargeApRequest request) {
        checkDuplicateOrder(request.orderId());

        // PG 검증은 실제 운영 환경에서 외부 API 호출로 교체 (현재 stub)
        validatePayment(request.paymentKey(), request.amount());

        Wallet wallet =
                walletRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        wallet.addAp(request.amount());

        markOrderProcessed(request.orderId());

        return new ChargeApResponse(
                wallet.getAvailableAp(), request.amount(), wallet.getUpdatedAt());
    }

    private void checkDuplicateOrder(String orderId) {
        String key = ORDER_KEY_PREFIX + orderId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER);
        }
    }

    private void markOrderProcessed(String orderId) {
        try {
            redisTemplate.opsForValue().set(ORDER_KEY_PREFIX + orderId, true, ORDER_TTL);
        } catch (Exception e) {
            log.warn("결제 주문 Redis 기록 실패 - orderId: {}", orderId, e);
        }
    }

    private void validatePayment(String paymentKey, int amount) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT);
        }
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT);
        }
    }
}
