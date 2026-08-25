package com.territorial.auction.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.user.dto.ChargeApRequest;
import com.territorial.auction.domain.user.dto.ChargeApResponse;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks private PaymentService paymentService;

    @Mock private WalletRepository walletRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private User sampleUser() {
        User user =
                User.builder()
                        .username("testuser")
                        .email("test@test.com")
                        .passwordHash("hashed")
                        .nickname("테스터")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private Wallet walletWithAp(User user, int ap) {
        Wallet wallet = Wallet.builder().user(user).build();
        ReflectionTestUtils.setField(wallet, "availableAp", ap);
        return wallet;
    }

    // ─── chargeAp() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("chargeAp()")
    class ChargeAp {

        @Test
        @DisplayName("AP 충전 성공 — 잔액 증가 확인")
        void success() {
            User user = sampleUser();
            Wallet wallet = walletWithAp(user, 300); // 기존 300AP

            given(redisTemplate.hasKey("payment:order:order_123")).willReturn(false);
            given(walletRepository.findById(1L)).willReturn(Optional.of(wallet));
            given(redisTemplate.opsForValue()).willReturn(valueOps);

            ChargeApRequest request = new ChargeApRequest(1000, "pg_key_abc", "order_123");
            ChargeApResponse response = paymentService.chargeAp(1L, request);

            assertThat(response.chargedAmount()).isEqualTo(1000);
            assertThat(response.availableAP()).isEqualTo(1300); // 300 + 1000
        }

        @Test
        @DisplayName("중복 주문 ID → DUPLICATE_ORDER")
        void duplicate_order() {
            given(redisTemplate.hasKey("payment:order:dup_order")).willReturn(true);

            ChargeApRequest request = new ChargeApRequest(1000, "pg_key_abc", "dup_order");
            assertThatThrownBy(() -> paymentService.chargeAp(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_ORDER);

            then(walletRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("유저 없음 → USER_NOT_FOUND")
        void user_not_found() {
            given(redisTemplate.hasKey("payment:order:order_xyz")).willReturn(false);
            given(walletRepository.findById(99L)).willReturn(Optional.empty());

            ChargeApRequest request = new ChargeApRequest(500, "pg_key", "order_xyz");
            assertThatThrownBy(() -> paymentService.chargeAp(99L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("빈 paymentKey → INVALID_PAYMENT")
        void invalid_payment_key() {
            given(redisTemplate.hasKey("payment:order:order_abc")).willReturn(false);

            ChargeApRequest request = new ChargeApRequest(1000, "", "order_abc");
            assertThatThrownBy(() -> paymentService.chargeAp(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PAYMENT);
        }
    }
}
