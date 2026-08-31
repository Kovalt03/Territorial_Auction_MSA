package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletCommandRepository;
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
    private final WalletCommandRepository walletCommandRepository;

    @Transactional
    public String bidEscrow(
            Long auctionId,
            Long bidderId,
            int bidAmount,
            Long previousBidderId,
            Integer previousAmount) {
        User bidder = findUserOrThrow(bidderId);
        validateCommand(auctionId, bidAmount);
        String commandKey = "BID_ESCROW:" + auctionId + ":" + bidderId + ":" + bidAmount;
        String fingerprint =
                bidderId + ":" + bidAmount + ":" + previousBidderId + ":" + previousAmount;
        if (isReplay(commandKey, fingerprint)) {
            return bidder.getNickname();
        }
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
    public void consumeLocked(Long winnerId, int finalPrice, Long auctionId) {
        validateCommand(auctionId, finalPrice);
        if (isReplay("CONSUME:" + auctionId, winnerId + ":" + finalPrice)) {
            return;
        }
        lockWalletOrThrow(winnerId).consumeLockedAp(finalPrice);
    }

    @Transactional
    public void refundLocked(Long bidderId, int amount, Long auctionId) {
        validateCommand(auctionId, amount);
        if (isReplay("REFUND:" + auctionId, bidderId + ":" + amount)) {
            return;
        }
        lockWalletOrThrow(bidderId).refundLockedAp(amount);
    }

    /** 일반 AP 소비(건물·아이템·시즌 등). commandKey로 멱등. 잔액 부족 시 INSUFFICIENT_AP. */
    @Transactional
    public void spend(Long userId, int amount, String commandKey) {
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_WALLET_AMOUNT);
        }
        if (isReplay("SPEND:" + commandKey, userId + ":" + amount)) {
            return;
        }
        lockWalletOrThrow(userId).spendAp(amount);
    }

    /** 보상: 앞선 소비를 되돌린다(호출 측 로컬 트랜잭션 실패 시). commandKey로 멱등. */
    @Transactional
    public void credit(Long userId, int amount, String commandKey) {
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_WALLET_AMOUNT);
        }
        if (isReplay("CREDIT:" + commandKey, userId + ":" + amount)) {
            return;
        }
        lockWalletOrThrow(userId).addAp(amount);
    }

    /** 관리자 AP 조정(delta 증감). commandKey로 멱등. 결과 음수면 INSUFFICIENT_AP. */
    @Transactional
    public void adjust(Long userId, int delta, String commandKey) {
        if (delta == 0) {
            return;
        }
        if (isReplay("ADJUST:" + commandKey, userId + ":" + delta)) {
            return;
        }
        lockWalletOrThrow(userId).adjustAvailableAp(delta);
    }

    private void validateCommand(Long auctionId, int amount) {
        if (auctionId == null || amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_WALLET_AMOUNT);
        }
    }

    private boolean isReplay(String commandKey, String fingerprint) {
        if (walletCommandRepository.reserve(commandKey, fingerprint) == 1) {
            return false;
        }
        if (walletCommandRepository.matches(commandKey, fingerprint)) {
            return true;
        }
        throw new CustomException(ErrorCode.WALLET_COMMAND_CONFLICT);
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
