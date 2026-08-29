package com.territorial.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.global.exception.ErrorCode;
import java.util.Optional;
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

    @Test
    void bidEscrowLocksBidderAndRefundsPreviousBidder() {
        Wallet bidderWallet = wallet(3L, 5000, 0);
        Wallet previousWallet = wallet(1L, 0, 1000);
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L, "새왕")));
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(previousWallet));
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(bidderWallet));

        assertThat(walletService.bidEscrow(3L, 1500, 1L, 1000)).isEqualTo("새왕");
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

        assertThatThrownBy(() -> walletService.bidEscrow(3L, 1000, 1L, 1000))
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

        walletService.bidEscrow(3L, 1500, 1L, 1000);

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdWithLock(1L);
        order.verify(walletRepository).findByIdWithLock(3L);
    }

    @Test
    void unknownBidderIsRejected() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.bidEscrow(9L, 1000, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void consumeLockedRejectsAmountLargerThanLockedBalance() {
        given(walletRepository.findByIdWithLock(3L)).willReturn(Optional.of(wallet(3L, 0, 500)));

        assertThatThrownBy(() -> walletService.consumeLocked(3L, 1000))
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
