package com.territorial.auction.domain.user.client;

/** user-service 지갑 명령/조회 응답 — 갱신된 잔액. */
public record WalletSnapshot(int availableAp, int lockedAp) {}
