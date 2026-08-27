package com.territorial.auction.domain.user.controller;

import com.territorial.auction.domain.user.dto.BidEscrowRequest;
import com.territorial.auction.domain.user.dto.BidEscrowResponse;
import com.territorial.auction.domain.user.dto.ConsumeLockedRequest;
import com.territorial.auction.domain.user.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class WalletInternalController {
    private final WalletService walletService;

    // 예외 상태코드는 GlobalExceptionHandler 단일 소스를 따른다
    // (INSUFFICIENT_AP=409, USER_NOT_FOUND=404). 내부 전용 매핑을 두지 않는다.
    @PostMapping("/bid-escrow")
    public ResponseEntity<BidEscrowResponse> bidEscrow(@RequestBody BidEscrowRequest request) {
        String nickname =
                walletService.bidEscrow(
                        request.bidderId(),
                        request.bidAmount(),
                        request.previousBidderId(),
                        request.previousAmount());
        return ResponseEntity.ok(new BidEscrowResponse(nickname));
    }

    @PostMapping("/consume-locked")
    public ResponseEntity<Void> consumeLocked(@RequestBody ConsumeLockedRequest request) {
        walletService.consumeLocked(request.winnerId(), request.finalPrice());
        return ResponseEntity.ok().build();
    }
}
