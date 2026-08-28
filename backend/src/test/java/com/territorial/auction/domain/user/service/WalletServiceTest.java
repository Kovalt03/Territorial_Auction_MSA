package com.territorial.auction.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @InjectMocks private WalletService walletService;

    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;

    private User user(long id, String nickname) {
        User u =
                User.builder()
                        .username("u" + id)
                        .email(id + "@x")
                        .passwordHash("h")
                        .nickname(nickname)
                        .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Wallet wallet(long userId, int available, int locked) {
        Wallet w = Wallet.builder().user(user(userId, "u" + userId)).build();
        ReflectionTestUtils.setField(w, "userId", userId);
        ReflectionTestUtils.setField(w, "availableAp", available);
        ReflectionTestUtils.setField(w, "lockedAp", locked);
        return w;
    }

    @Nested
    @DisplayName("bidEscrow()")
    class BidEscrow {

        @Test
        @DisplayName("이전 입찰자 없음 → 신규 입찰자 AP 잠금 + 닉네임 반환")
        void bidEscrow_noPrevious() {
            given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "입찰왕")));
            Wallet bidder = wallet(3L, 5000, 0);
            given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidder));

            String nickname = walletService.bidEscrow(3L, 1000, null, null);

            assertThat(nickname).isEqualTo("입찰왕");
            assertThat(bidder.getAvailableAp()).isEqualTo(4000);
            assertThat(bidder.getLockedAp()).isEqualTo(1000);
        }

        @Test
        @DisplayName("이전 입찰자 있음 → 이전 잠금 환불 + 신규 잠금")
        void bidEscrow_refundsPrevious() {
            given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
            Wallet bidder = wallet(3L, 5000, 0);
            Wallet previous = wallet(1L, 0, 1000); // 이전에 1000 잠금
            given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(previous));

            walletService.bidEscrow(3L, 1500, 1L, 1000);

            assertThat(previous.getLockedAp()).isZero(); // 환불됨
            assertThat(previous.getAvailableAp()).isEqualTo(1000);
            assertThat(bidder.getLockedAp()).isEqualTo(1500);
        }

        @Test
        @DisplayName("데드락 회피 — previousId < bidderId면 작은 ID부터 잠금")
        void bidEscrow_locksInAscendingOrder_prevSmaller() {
            given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
            given(walletRepository.findByIdWithLock(1L))
                    .willReturn(Optional.of(wallet(1L, 0, 1000)));
            given(walletRepository.findByIdWithLock(3L))
                    .willReturn(Optional.of(wallet(3L, 5000, 0)));

            walletService.bidEscrow(3L, 1500, 1L, 1000);

            InOrder order = inOrder(walletRepository);
            order.verify(walletRepository).findByIdWithLock(1L); // 작은 ID 먼저
            order.verify(walletRepository).findByIdWithLock(3L);
        }

        @Test
        @DisplayName("데드락 회피 — previousId > bidderId면 작은 ID(bidder)부터 잠금")
        void bidEscrow_locksInAscendingOrder_prevLarger() {
            given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
            given(walletRepository.findByIdWithLock(3L))
                    .willReturn(Optional.of(wallet(3L, 5000, 0)));
            given(walletRepository.findByIdWithLock(5L))
                    .willReturn(Optional.of(wallet(5L, 0, 1000)));

            walletService.bidEscrow(3L, 1500, 5L, 1000);

            InOrder order = inOrder(walletRepository);
            order.verify(walletRepository).findByIdWithLock(3L); // 작은 ID(bidder) 먼저
            order.verify(walletRepository).findByIdWithLock(5L);
        }

        @Test
        @DisplayName("AP 잔액 부족 → INSUFFICIENT_AP, 이전 입찰자 환불 안 함")
        void bidEscrow_insufficientAp() {
            given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
            Wallet bidder = wallet(3L, 500, 0); // 부족
            Wallet previous = wallet(1L, 0, 1000);
            given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidder));
            given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(previous));

            assertThatThrownBy(() -> walletService.bidEscrow(3L, 1000, 1L, 1000))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
            assertThat(previous.getLockedAp()).isEqualTo(1000); // 환불되지 않음
            assertThat(bidder.getLockedAp()).isZero();
        }

        @Test
        @DisplayName("유저 없음 → USER_NOT_FOUND")
        void bidEscrow_userNotFound() {
            given(userRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.bidEscrow(9L, 1000, null, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("consumeLocked() / refundLocked()")
    class ConsumeRefund {

        @Test
        @DisplayName("consumeLocked → 잠금 AP 소비")
        void consumeLocked() {
            Wallet winner = wallet(3L, 0, 2000);
            given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(winner));

            walletService.consumeLocked(3L, 2000);

            assertThat(winner.getLockedAp()).isZero();
        }

        @Test
        @DisplayName("refundLocked → 잠금 AP 환불")
        void refundLocked() {
            Wallet bidder = wallet(3L, 0, 2000);
            given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidder));

            walletService.refundLocked(3L, 2000);

            assertThat(bidder.getLockedAp()).isZero();
            assertThat(bidder.getAvailableAp()).isEqualTo(2000);
        }

        @Test
        @DisplayName("consumeLocked — 지갑 없음 → USER_NOT_FOUND")
        void consumeLocked_notFound() {
            given(walletRepository.findByIdWithLock(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.consumeLocked(9L, 1000))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
