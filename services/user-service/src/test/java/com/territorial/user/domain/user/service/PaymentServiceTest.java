package com.territorial.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChargeApRequest;
import com.territorial.user.domain.user.dto.ChargeApResponse;
import com.territorial.user.domain.user.dto.WalletSnapshot;
import com.territorial.user.domain.user.entity.PaymentOrder;
import com.territorial.user.domain.user.repository.PaymentOrderRepository;
import com.territorial.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks private PaymentService paymentService;

    @Mock private WalletService walletService;
    @Mock private PaymentOrderRepository paymentOrderRepository;

    @Nested
    @DisplayName("chargeAp()")
    class ChargeAp {

        @Test
        @DisplayName("AP 충전 성공 — 잔액 증가·주문 기록")
        void success() {
            given(paymentOrderRepository.existsById("order_123")).willReturn(false);
            given(walletService.credit(eq(1L), eq(1000), anyString()))
                    .willReturn(new WalletSnapshot(1300, 0));

            ChargeApRequest request = new ChargeApRequest(1000, "pg_key_abc", "order_123");
            ChargeApResponse response = paymentService.chargeAp(1L, request);

            assertThat(response.chargedAmount()).isEqualTo(1000);
            assertThat(response.availableAP()).isEqualTo(1300);
            then(paymentOrderRepository).should().save(any(PaymentOrder.class));
        }

        @Test
        @DisplayName("중복 주문 ID → DUPLICATE_ORDER")
        void duplicate_order() {
            given(paymentOrderRepository.existsById("dup_order")).willReturn(true);

            ChargeApRequest request = new ChargeApRequest(1000, "pg_key_abc", "dup_order");
            assertThatThrownBy(() -> paymentService.chargeAp(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_ORDER);

            then(walletService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("유저 없음 → USER_NOT_FOUND")
        void user_not_found() {
            given(paymentOrderRepository.existsById("order_xyz")).willReturn(false);
            willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                    .given(walletService)
                    .credit(eq(99L), anyInt(), anyString());

            ChargeApRequest request = new ChargeApRequest(500, "pg_key", "order_xyz");
            assertThatThrownBy(() -> paymentService.chargeAp(99L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("빈 paymentKey → INVALID_PAYMENT")
        void invalid_payment_key() {
            ChargeApRequest request = new ChargeApRequest(1000, "", "order_abc");
            assertThatThrownBy(() -> paymentService.chargeAp(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PAYMENT);

            then(walletService).shouldHaveNoInteractions();
        }
    }
}
