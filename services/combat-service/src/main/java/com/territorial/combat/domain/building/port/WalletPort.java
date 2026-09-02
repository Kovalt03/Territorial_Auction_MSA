package com.territorial.combat.domain.building.port;

public interface WalletPort {

    WalletSnapshot spend(Long userId, int amount, String commandKey);

    record WalletSnapshot(int availableAp) {}
}
