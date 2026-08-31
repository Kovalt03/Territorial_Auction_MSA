package com.territorial.user.domain.user.dto;

import com.territorial.user.domain.user.entity.Wallet;

/** 지갑 명령/조회 응답 — 갱신된 잔액. 호출 측이 재조회 없이 표시할 수 있게 한다. */
public record WalletSnapshot(int availableAp, int lockedAp) {

    public static WalletSnapshot of(Wallet wallet) {
        return new WalletSnapshot(wallet.getAvailableAp(), wallet.getLockedAp());
    }
}
