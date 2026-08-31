package com.territorial.user.domain.user.controller;

import com.territorial.user.domain.user.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway로 라우팅하지 않는 Auction 전용 지갑 계약. */
@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class WalletInternalController {

    private final WalletService walletService;

    @PostMapping("/bid-escrow")
    public ResponseEntity<BidEscrowResponse> bidEscrow(@RequestBody BidEscrowRequest request) {
        return ResponseEntity.ok(
                new BidEscrowResponse(
                        walletService.bidEscrow(
                                request.auctionId(),
                                request.bidderId(),
                                request.bidAmount(),
                                request.previousBidderId(),
                                request.previousAmount())));
    }

    @PostMapping("/consume-locked")
    public ResponseEntity<Void> consumeLocked(@RequestBody ConsumeLockedRequest request) {
        walletService.consumeLocked(request.winnerId(), request.finalPrice(), request.auctionId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refund-locked")
    public ResponseEntity<Void> refundLocked(@RequestBody RefundLockedRequest request) {
        walletService.refundLocked(request.bidderId(), request.amount(), request.auctionId());
        return ResponseEntity.ok().build();
    }

    // ── 일반 AP 명령 (건물·아이템·시즌·admin이 호출) ──────────────────────────

    @PostMapping("/spend")
    public ResponseEntity<Void> spend(@RequestBody SpendRequest request) {
        walletService.spend(request.userId(), request.amount(), request.commandKey());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/credit")
    public ResponseEntity<Void> credit(@RequestBody CreditRequest request) {
        walletService.credit(request.userId(), request.amount(), request.commandKey());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/adjust")
    public ResponseEntity<Void> adjust(@RequestBody AdjustRequest request) {
        walletService.adjust(request.userId(), request.delta(), request.commandKey());
        return ResponseEntity.ok().build();
    }

    public record SpendRequest(Long userId, int amount, String commandKey) {}

    public record CreditRequest(Long userId, int amount, String commandKey) {}

    public record AdjustRequest(Long userId, int delta, String commandKey) {}

    public record BidEscrowRequest(
            Long auctionId,
            Long bidderId,
            int bidAmount,
            Long previousBidderId,
            Integer previousAmount) {}

    public record BidEscrowResponse(String bidderNickname) {}

    public record ConsumeLockedRequest(Long winnerId, int finalPrice, Long auctionId) {}

    public record RefundLockedRequest(Long bidderId, int amount, Long auctionId) {}
}
