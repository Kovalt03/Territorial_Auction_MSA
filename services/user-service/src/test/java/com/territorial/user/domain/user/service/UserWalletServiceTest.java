package com.territorial.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChargeApRequest;
import com.territorial.user.domain.user.dto.ChargeApResponse;
import com.territorial.user.domain.user.dto.MyWalletResponse;
import com.territorial.user.domain.user.entity.GlobalVault;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.GlobalVaultRepository;
import com.territorial.user.domain.user.repository.PaymentOrderRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserWalletServiceTest {

    @InjectMocks private UserWalletService userWalletService;
    @Mock private WalletRepository walletRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;

    @Test
    void getMyWalletReturnsApAndGpFromOwnedTables() {
        given(walletRepository.findById(1L)).willReturn(Optional.of(wallet(1000, 200)));
        given(globalVaultRepository.findById(1L)).willReturn(Optional.of(vault(3000)));

        MyWalletResponse result = userWalletService.getMyWallet(1L);

        assertThat(result).isEqualTo(new MyWalletResponse(3000, 1000, 200));
    }

    @Test
    void chargeApReservesOrderAndUpdatesLockedWallet() {
        Wallet wallet = wallet(1000, 0);
        ChargeApRequest request = new ChargeApRequest(500, "payment-key", "order-1");
        given(paymentOrderRepository.reserve("order-1", 1L, 500)).willReturn(1);
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(wallet));

        ChargeApResponse result = userWalletService.chargeAp(1L, request);

        assertThat(result.availableAP()).isEqualTo(1500);
        assertThat(result.chargedAmount()).isEqualTo(500);
    }

    @Test
    void duplicateOrderDoesNotTouchWallet() {
        ChargeApRequest request = new ChargeApRequest(500, "payment-key", "order-1");
        given(paymentOrderRepository.reserve("order-1", 1L, 500)).willReturn(0);

        assertThatThrownBy(() -> userWalletService.chargeAp(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_ORDER);
        verify(walletRepository, never()).findByIdWithLock(1L);
    }

    private Wallet wallet(int availableAp, int lockedAp) {
        Wallet wallet = Wallet.builder().user(user()).build();
        ReflectionTestUtils.setField(wallet, "userId", 1L);
        ReflectionTestUtils.setField(wallet, "availableAp", availableAp);
        ReflectionTestUtils.setField(wallet, "lockedAp", lockedAp);
        return wallet;
    }

    private GlobalVault vault(int storedGp) {
        GlobalVault vault = GlobalVault.builder().user(user()).build();
        ReflectionTestUtils.setField(vault, "userId", 1L);
        ReflectionTestUtils.setField(vault, "storedGp", storedGp);
        return vault;
    }

    private User user() {
        User user =
                User.builder()
                        .username("user")
                        .email("user@example.com")
                        .passwordHash("hash")
                        .nickname("nickname")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
