package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChargeApRequest;
import com.territorial.user.domain.user.dto.ChargeApResponse;
import com.territorial.user.domain.user.dto.MyWalletResponse;
import com.territorial.user.domain.user.entity.GlobalVault;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.GlobalVaultRepository;
import com.territorial.user.domain.user.repository.PaymentOrderRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserWalletService {

    private final WalletRepository walletRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    public MyWalletResponse getMyWallet(Long userId) {
        Wallet wallet = findWalletOrThrow(userId);
        int storedGp =
                globalVaultRepository.findById(userId).map(GlobalVault::getStoredGp).orElse(0);
        return new MyWalletResponse(storedGp, wallet.getAvailableAp(), wallet.getLockedAp());
    }

    @Transactional
    public ChargeApResponse chargeAp(Long userId, ChargeApRequest request) {
        validatePayment(request);
        if (paymentOrderRepository.reserve(request.orderId(), userId, request.amount()) == 0) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER);
        }
        Wallet wallet = lockWalletOrThrow(userId);
        wallet.addAp(request.amount());
        return new ChargeApResponse(wallet.getAvailableAp(), request.amount(), LocalDateTime.now());
    }

    private Wallet findWalletOrThrow(Long userId) {
        return walletRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Wallet lockWalletOrThrow(Long userId) {
        return walletRepository
                .findByIdWithLock(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void validatePayment(ChargeApRequest request) {
        if (request.paymentKey().isBlank() || request.amount() <= 0) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT);
        }
    }
}
