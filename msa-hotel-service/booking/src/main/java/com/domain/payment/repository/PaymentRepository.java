package com.domain.payment.repository;

import com.domain.payment.entity.Payment;
import com.domain.payment.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    // 예약 확정이 이벤트로 비동기 처리되는 동안 예약은 PENDING으로 남아 있으므로,
    // 예약 상태만으로는 중복 결제를 막을 수 없다. 결제 이력으로 한 번 더 검증한다.
    boolean existsByBookingIdAndPaymentStatus(Long bookingId, PaymentStatus paymentStatus);
}
