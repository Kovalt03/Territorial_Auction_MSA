package com.territorial.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletCommandRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock private WalletCommandRepository walletCommandRepository;

    @BeforeEach
    void setUp() {
        lenient().when(walletCommandRepository.reserve(anyString(), anyString())).thenReturn(1);
    }

    @Test
    void bidEscrowLocksBidderAndRefundsPreviousBidder() {
        Wallet bidderWallet = wallet(3L, 5000, 0);
        Wallet previousWallet = wallet(1L, 0, 1000);
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(previousWallet));
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidderWallet));

        assertThat(walletService.bidEscrow(10L, 3L, 1500, 1L, 1000)).isEqualTo("새왕");
        assertThat(previousWallet.getAvailableAp()).isEqualTo(1000);
        assertThat(previousWallet.getLockedAp()).isZero();
        assertThat(bidderWallet.getAvailableAp()).isEqualTo(3500);
        assertThat(bidderWallet.getLockedAp()).isEqualTo(1500);
    }

    @Test
    void insufficientBalanceDoesNotRefundPreviousBidder() {
        Wallet bidderWallet = wallet(3L, 500, 0);
        Wallet previousWallet = wallet(1L, 0, 1000);
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(previousWallet));
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidderWallet));

        assertThatThrownBy(() -> walletService.bidEscrow(10L, 3L, 1000, 1L, 1000))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        assertThat(previousWallet.getLockedAp()).isEqualTo(1000);
    }

    @Test
    void escrowLocksWalletsInAscendingUserIdOrder() {
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(wallet(1L, 0, 1000)));
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(wallet(3L, 5000, 0)));

        walletService.bidEscrow(10L, 3L, 1500, 1L, 1000);

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdWithLock(1L);
        order.verify(walletRepository).findByIdWithLock(3L);
    }

    @Test
    void unknownBidderIsRejected() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.bidEscrow(10L, 9L, 1000, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void compensateBidEscrowReversesLockAndRefund() {
        // escrow 직후 상태: 새 입찰자(3) 1500 잠김, 이전 입찰자(1) 1500 환불받아 available
        Wallet bidderWallet = wallet(3L, 3500, 1500);
        Wallet previousWallet = wallet(1L, 1000, 0);
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(previousWallet));
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidderWallet));

        walletService.compensateBidEscrow(10L, 3L, 1500, 1L, 1000);

        // 새 입찰자: 잠금 해제 → available 복원
        assertThat(bidderWallet.getAvailableAp()).isEqualTo(5000);
        assertThat(bidderWallet.getLockedAp()).isZero();
        // 이전 입찰자: 재잠금 → escrow 이전 상태로
        assertThat(previousWallet.getAvailableAp()).isZero();
        assertThat(previousWallet.getLockedAp()).isEqualTo(1000);
    }

    @Test
    void compensateBidEscrowWithoutPreviousBidderOnlyUnlocksBidder() {
        Wallet bidderWallet = wallet(3L, 3500, 1500);
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidderWallet));

        walletService.compensateBidEscrow(10L, 3L, 1500, null, null);

        assertThat(bidderWallet.getAvailableAp()).isEqualTo(5000);
        assertThat(bidderWallet.getLockedAp()).isZero();
    }

    @Test
    void duplicateCompensateCommandDoesNotChangeWalletAgain() {
        given(walletCommandRepository.reserve("BID_COMPENSATE:10:3:1500", "3:1500:1:1000"))
                .willReturn(0);
        given(walletCommandRepository.matches("BID_COMPENSATE:10:3:1500", "3:1500:1:1000"))
                .willReturn(true);

        walletService.compensateBidEscrow(10L, 3L, 1500, 1L, 1000);

        verify(walletRepository, never()).findByIdWithLock(3L);
    }

    @Test
    void consumeLockedRejectsAmountLargerThanLockedBalance() {
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(wallet(3L, 0, 500)));

        assertThatThrownBy(() -> walletService.consumeLocked(3L, 1000, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
    }

    @Test
    void duplicateConsumeCommandDoesNotChangeWalletAgain() {
        given(walletCommandRepository.reserve("CONSUME:10", "3:1000")).willReturn(0);
        given(walletCommandRepository.matches("CONSUME:10", "3:1000")).willReturn(true);

        walletService.consumeLocked(3L, 1000, 10L);

        verify(walletRepository, never()).findByIdWithLock(3L);
    }

    @Test
    void duplicateCommandWithDifferentPayloadIsRejected() {
        given(walletCommandRepository.reserve("CONSUME:10", "3:1000")).willReturn(0);
        given(walletCommandRepository.matches("CONSUME:10", "3:1000")).willReturn(false);

        assertThatThrownBy(() -> walletService.consumeLocked(3L, 1000, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WALLET_COMMAND_CONFLICT);

        verify(walletRepository, never()).findByIdWithLock(3L);
    }

    @Test
    void refundRejectsAmountLargerThanLockedBalance() {
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(wallet(3L, 0, 500)));

        assertThatThrownBy(() -> walletService.refundLocked(3L, 1000, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
    }

    @Test
    void bidEscrowRejectsNonPositiveAmount() {
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));

        assertThatThrownBy(() -> walletService.bidEscrow(10L, 3L, 0, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_WALLET_AMOUNT);
    }

    @Test
    void spendDeductsAvailableAp() {
        Wallet w = wallet(3L, 5000, 0);
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(w));

        var snapshot = walletService.spend(3L, 1000, "BUILDING:1");

        assertThat(w.getAvailableAp()).isEqualTo(4000);
        assertThat(snapshot.availableAp()).isEqualTo(4000);
    }

    @Test
    void spendRejectsInsufficientBalance() {
        Wallet w = wallet(3L, 500, 0);
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(w));

        assertThatThrownBy(() -> walletService.spend(3L, 1000, "BUILDING:1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        assertThat(w.getAvailableAp()).isEqualTo(500);
    }

    @Test
    void spendIsIdempotentOnReplay() {
        Wallet w = wallet(3L, 4000, 0); // 이미 차감된 상태
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(w));
        given(walletCommandRepository.reserve(anyString(), anyString())).willReturn(0);
        given(walletCommandRepository.matches(anyString(), anyString())).willReturn(true);

        var snapshot = walletService.spend(3L, 1000, "BUILDING:1"); // 재전달 → 재차감 안 함

        assertThat(w.getAvailableAp()).isEqualTo(4000);
        assertThat(snapshot.availableAp()).isEqualTo(4000);
    }

    @Test
    void creditAddsAvailableAp() {
        Wallet w = wallet(3L, 0, 0);
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(w));

        walletService.credit(3L, 1000, "BUILDING:1:compensate");

        assertThat(w.getAvailableAp()).isEqualTo(1000);
    }

    @Test
    void adjustAllowsNegativeDeltaAboveZero() {
        Wallet w = wallet(3L, 1000, 0);
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(w));

        walletService.adjust(3L, -400, "ADMIN:1");

        assertThat(w.getAvailableAp()).isEqualTo(600);
    }

    @Test
    void adjustRejectsWhenResultNegative() {
        Wallet w = wallet(3L, 100, 0);
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(w));

        assertThatThrownBy(() -> walletService.adjust(3L, -500, "ADMIN:1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
    }

    private User user(long id, String nickname) {
        User user =
                User.builder()
                        .username("u" + id)
                        .email(id + "@example.com")
                        .passwordHash("hash")
                        .nickname(nickname)
                        .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Wallet wallet(long userId, int availableAp, int lockedAp) {
        Wallet wallet = Wallet.builder().user(user(userId, "u" + userId)).build();
        ReflectionTestUtils.setField(wallet, "userId", userId);
        ReflectionTestUtils.setField(wallet, "availableAp", availableAp);
        ReflectionTestUtils.setField(wallet, "lockedAp", lockedAp);
        return wallet;
    }
}
