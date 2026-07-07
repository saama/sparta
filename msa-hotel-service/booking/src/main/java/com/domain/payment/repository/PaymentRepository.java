package com.domain.payment.repository;

import com.domain.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);
}
