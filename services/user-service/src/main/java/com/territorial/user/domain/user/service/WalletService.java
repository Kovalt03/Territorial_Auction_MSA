package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.WalletSnapshot;
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

    /**
     * escrow 보상: 앞선 {@link #bidEscrow}를 역전한다(호출 측 로컬 트랜잭션 롤백 시). 새 입찰자 잠금 해제 + 이전 입찰자 재잠금. commandKey로
     * 멱등. 데드락 방지를 위해 escrow와 동일한 오름차순 userId 순으로 락을 잡는다.
     */
    @Transactional
    public void compensateBidEscrow(
            Long auctionId,
            Long bidderId,
            int bidAmount,
            Long previousBidderId,
            Integer previousAmount) {
        validateCommand(auctionId, bidAmount);
        String commandKey = "BID_COMPENSATE:" + auctionId + ":" + bidderId + ":" + bidAmount;
        String fingerprint =
                bidderId + ":" + bidAmount + ":" + previousBidderId + ":" + previousAmount;
        if (isReplay(commandKey, fingerprint)) {
            return;
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
        bidderWallet.refundLockedAp(bidAmount); // escrow의 lockAp 역전
        relockPreviousBid(previousWallet, previousAmount); // escrow의 refundLockedAp 역전
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

    /** 일반 AP 소비(건물·아이템·시즌 등). commandKey로 멱등. 잔액 부족 시 INSUFFICIENT_AP. 갱신 잔액 반환. */
    @Transactional
    public WalletSnapshot spend(Long userId, int amount, String commandKey) {
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_WALLET_AMOUNT);
        }
        Wallet wallet = lockWalletOrThrow(userId);
        if (!isReplay("SPEND:" + commandKey, userId + ":" + amount)) {
            wallet.spendAp(amount);
        }
        return WalletSnapshot.of(wallet);
    }

    /** 보상: 앞선 소비를 되돌린다(호출 측 로컬 트랜잭션 실패 시). commandKey로 멱등. */
    @Transactional
    public WalletSnapshot credit(Long userId, int amount, String commandKey) {
        if (amount <= 0) {
            throw new CustomException(ErrorCode.INVALID_WALLET_AMOUNT);
        }
        Wallet wallet = lockWalletOrThrow(userId);
        if (!isReplay("CREDIT:" + commandKey, userId + ":" + amount)) {
            wallet.addAp(amount);
        }
        return WalletSnapshot.of(wallet);
    }

    /** 관리자 AP 조정(delta 증감). commandKey로 멱등. 결과 음수면 INSUFFICIENT_AP. */
    @Transactional
    public WalletSnapshot adjust(Long userId, int delta, String commandKey) {
        Wallet wallet = lockWalletOrThrow(userId);
        if (delta != 0 && !isReplay("ADJUST:" + commandKey, userId + ":" + delta)) {
            wallet.adjustAvailableAp(delta);
        }
        return WalletSnapshot.of(wallet);
    }

    /** 조회: 특정 유저 지갑 상태 (admin·표시용). */
    public WalletSnapshot getWallet(Long userId) {
        Wallet wallet =
                walletRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return WalletSnapshot.of(wallet);
    }

    /** 조회: 전체 가용 AP 합(admin 대시보드). */
    public long sumAvailableAp() {
        return walletRepository.sumAvailableAp();
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

    private void relockPreviousBid(Wallet previousWallet, Integer previousAmount) {
        if (previousWallet != null && previousAmount != null) {
            previousWallet.lockAp(previousAmount);
        }
    }
}
