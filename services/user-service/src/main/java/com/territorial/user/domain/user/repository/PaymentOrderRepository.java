package com.territorial.user.domain.user.repository;

import com.territorial.user.domain.user.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {}
