package com.territorial.auction.domain.user.client;

/**
 * 모놀리식 → user-service 지갑(AP) 명령·조회 포트. AP 소유는 user-service이므로 모놀리식 도메인은 이 클라이언트로만 지갑을 다룬다.
 * commandKey는 멱등성·보상 짝맞춤용 안정 키.
 */
public interface WalletClient {

    WalletSnapshot spend(Long userId, int amount, String commandKey);

    WalletSnapshot credit(Long userId, int amount, String commandKey);

    WalletSnapshot adjust(Long userId, int delta, String commandKey);

    WalletSnapshot getWallet(Long userId);

    long sumAvailableAp();
}
