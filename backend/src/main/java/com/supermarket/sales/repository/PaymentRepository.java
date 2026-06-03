package com.supermarket.sales.repository;

import com.supermarket.sales.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findBySaleId(Long saleId);
}
