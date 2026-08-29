package com.territorial.user.domain.user.repository;

import com.territorial.user.domain.user.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {}
