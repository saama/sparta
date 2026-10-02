package com.domain.payment.repository;

import com.domain.payment.entity.Payment;
import com.domain.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    // 예약 확정이 이벤트로 비동기 처리되는 동안 예약은 PENDING으로 남아 있으므로,
    // 예약 상태만으로는 중복 결제를 막을 수 없다. 결제 이력으로 한 번 더 검증한다.
    boolean existsByBookingIdAndPaymentStatus(Long bookingId, PaymentStatus paymentStatus);

    // 환불 경로(결제 취소 API / 예약 취소 Saga)가 동시에 같은 결제를 환불하지 않도록 행을 잠근다.
    // 먼저 잠근 쪽이 REFUNDED로 바꾸고 커밋하면, 뒤따른 쪽은 최신 상태(REFUNDED)를 읽고 환불을 건너뛴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId AND p.paymentStatus = :status")
    List<Payment> findByBookingIdAndStatusWithLock(@Param("bookingId") Long bookingId,
        @Param("status") PaymentStatus status);
}
