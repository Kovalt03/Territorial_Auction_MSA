package com.territorial.user.domain.user.repository;

import com.territorial.user.domain.user.entity.Wallet;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends Repository<Wallet, Long> {

    @Modifying
    @Query(
            value =
                    "INSERT INTO payment_orders (order_id, user_id, amount) "
                            + "VALUES (:orderId, :userId, :amount) ON CONFLICT (order_id) DO NOTHING",
            nativeQuery = true)
    int reserve(
            @Param("orderId") String orderId,
            @Param("userId") Long userId,
            @Param("amount") int amount);
}
