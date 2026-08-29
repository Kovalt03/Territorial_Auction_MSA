package com.territorial.user.domain.user.repository;

import com.territorial.user.domain.user.entity.Wallet;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WalletCommandRepository extends Repository<Wallet, Long> {

    @Modifying
    @Query(
            value =
                    "INSERT INTO wallet_commands (command_key, request_fingerprint) "
                            + "VALUES (:commandKey, :fingerprint) "
                            + "ON CONFLICT (command_key) DO NOTHING",
            nativeQuery = true)
    int reserve(@Param("commandKey") String commandKey, @Param("fingerprint") String fingerprint);

    @Query(
            value =
                    "SELECT COUNT(*) = 1 FROM wallet_commands "
                            + "WHERE command_key = :commandKey "
                            + "AND request_fingerprint = :fingerprint",
            nativeQuery = true)
    boolean matches(
            @Param("commandKey") String commandKey, @Param("fingerprint") String fingerprint);
}
