package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChargeApRequest;
import com.territorial.user.domain.user.dto.ChargeApResponse;
import com.territorial.user.domain.user.dto.WalletSnapshot;
import com.territorial.user.domain.user.entity.PaymentOrder;
import com.territorial.user.domain.user.repository.PaymentOrderRepository;
import com.territorial.user.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AP 충전 — user-service가 Wallet 소유이므로 로컬 credit. orderId를 payment_orders에 기록해 멱등 보장. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final WalletService walletService;
    private final PaymentOrderRepository paymentOrderRepository;

    @Transactional
    public ChargeApResponse chargeAp(Long userId, ChargeApRequest request) {
        validatePayment(request.paymentKey(), request.amount());
        checkDuplicateOrder(request.orderId());

        // AP 소유는 로컬 Wallet. commandKey(orderId)로 지갑 명령도 이중 멱등.
        WalletSnapshot wallet =
                walletService.credit(userId, request.amount(), "CHARGE:" + request.orderId());
        paymentOrderRepository.save(
                PaymentOrder.builder()
                        .orderId(request.orderId())
                        .userId(userId)
                        .amount(request.amount())
                        .build());
        log.info(
                "AP 충전 완료. userId={}, orderId={}, amount={}",
                userId,
                request.orderId(),
                request.amount());

        return new ChargeApResponse(wallet.availableAp(), request.amount(), LocalDateTime.now());
    }

    private void checkDuplicateOrder(String orderId) {
        if (paymentOrderRepository.existsById(orderId)) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER);
        }
    }

    private void validatePayment(String paymentKey, int amount) {
        // PG 검증은 실제 운영 환경에서 외부 API 호출로 교체 (현재 stub)
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT);
        }
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT);
        }
    }
}
