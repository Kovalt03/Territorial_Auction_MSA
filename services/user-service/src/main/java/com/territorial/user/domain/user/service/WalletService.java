package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @Transactional
    public String bidEscrow(
            Long bidderId, int bidAmount, Long previousBidderId, Integer previousAmount) {
        User bidder = findUserOrThrow(bidderId);
        Wallet bidderWallet;
        Wallet previousWallet = null;
        if (previousBidderId == null) {
            bidderWallet = lockWalletOrThrow(bidderId);
        } else if (previousBidderId < bidderId) {
            previousWallet = lockWalletOrThrow(previousBidderId);
            bidderWallet = lockWalletOrThrow(bidderId);
        } else {
            bidderWallet = lockWalletOrThrow(bidderId);
            previousWallet = lockWalletOrThrow(previousBidderId);
        }
        validateAvailableAp(bidderWallet, bidAmount);
        refundPreviousBid(previousWallet, previousAmount);
        bidderWallet.lockAp(bidAmount);
        return bidder.getNickname();
    }

    @Transactional
    public void consumeLocked(Long winnerId, int finalPrice) {
        lockWalletOrThrow(winnerId).consumeLockedAp(finalPrice);
    }

    @Transactional
    public void refundLocked(Long bidderId, int amount) {
        lockWalletOrThrow(bidderId).refundLockedAp(amount);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Wallet lockWalletOrThrow(Long userId) {
        return walletRepository
                .findByIdWithLock(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateAvailableAp(Wallet wallet, int bidAmount) {
        if (wallet.getAvailableAp() < bidAmount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
    }

    private void refundPreviousBid(Wallet previousWallet, Integer previousAmount) {
        if (previousWallet != null && previousAmount != null) {
            previousWallet.refundLockedAp(previousAmount);
        }
    }
}
