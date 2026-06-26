package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByRequestNo(String requestNo);

    Optional<PaymentTransaction> findByChannelTradeNo(String channelTradeNo);

    List<PaymentTransaction> findByPaymentNoOrderByCreatedAtDesc(String paymentNo);
}
