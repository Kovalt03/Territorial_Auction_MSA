package com.territorial.auction.domain.user.service;

import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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
    public String bidEscrow(Long bidderId, int bidAmount, Long previousBidderId, Integer previousAmount) {
        User bidder = userRepository.findById(bidderId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Wallet bidderWallet;
        Wallet previousWallet = null;

        // 데드락 회피 : 항상 id 오름차순으로 락
        if (previousBidderId == null) {
            bidderWallet = lockOrThrow(bidderId);
        } else if (previousBidderId < bidderId) {
            previousWallet = lockOrThrow(previousBidderId);
            bidderWallet = lockOrThrow(bidderId);
        } else {
            bidderWallet = lockOrThrow(bidderId);
            previousWallet = lockOrThrow(previousBidderId);
        }

        if (bidderWallet.getAvailableAp() < bidAmount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
        }
        if (previousWallet != null && previousAmount != null) {
            previousWallet.refundLockedAp(previousAmount);
        }
        bidderWallet.lockAp(bidAmount);
        return bidder.getNickname();
    }

    /** 낙찰자의 잠금 AP를 소비(정산). auctionId는 추적/로깅용. */
    @Transactional
    public void consumeLocked(Long winnerId, int finalPrice) {
        Wallet wallet = lockOrThrow(winnerId);
        wallet.consumeLockedAp(finalPrice);
    }

    private Wallet lockOrThrow(Long bidderId) {
        return walletRepository.findByIdWithLock(bidderId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
