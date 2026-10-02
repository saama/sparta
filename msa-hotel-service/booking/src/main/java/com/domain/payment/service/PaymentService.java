package com.domain.payment.service;

import com.domain.booking.entity.Booking;
import com.domain.booking.entity.BookingStatus;
import com.domain.booking.repository.BookingRepository;
import com.domain.coupon.entity.UserCoupon;
import com.domain.coupon.repository.UserCouponRepository;
import com.domain.payment.client.PgClient;
import com.domain.payment.client.PgResponse;
import com.domain.payment.dto.request.PaymentCreateRequest;
import com.domain.payment.dto.response.PaymentResponse;
import com.domain.payment.entity.Payment;
import com.domain.payment.entity.PaymentStatus;
import com.domain.payment.event.PaymentCompletedEvent;
import com.domain.payment.repository.PaymentRepository;
import com.domain.room.entity.RoomStock;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 서비스
 *
 * <p>가상 PG({@link PgClient})를 통해 결제를 처리하며, 결제 성공 시 결제 완료 이벤트를 발행한다.
 * 예약 상태 CONFIRMED 전환은 이벤트를 구독하는 {@code PaymentEventConsumer}가 비동기로 처리한다.
 *
 * <p>결제 취소 흐름:
 * <ol>
 *   <li>PG 취소 요청</li>
 *   <li>Payment 상태 REFUNDED 처리</li>
 *   <li>Booking 상태 CANCELLED 처리</li>
 *   <li>객실 재고 원복</li>
 *   <li>사용된 쿠폰 복구 (있는 경우)</li>
 * </ol>
 *
 * <p>예약 취소 API로 예약이 먼저 취소된 경우에는 예약 취소 이벤트(Saga)를 통해
 * {@link #refundByBookingCancel}에서 환불이 진행된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserCouponRepository userCouponRepository;
    private final RoomStockRepository roomStockRepository;
    private final PgClient pgClient;
    // Kafka로 직접 보내지 않고 Spring 내부 이벤트로 발행 → 커밋 이후 PaymentEventProducer가 Kafka 전송
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 처리
     *
     * <p>PENDING 상태 예약에 대해서만 결제를 허용한다.
     * PG 결제 성공 시 Payment를 저장하고 {@link PaymentCompletedEvent}를 발행한다.
     *
     * @param userId  결제 요청 유저 ID
     * @param request 결제 수단 및 예약 ID
     * @return 결제 결과
     */
    @Transactional
    public PaymentResponse pay(Long userId, PaymentCreateRequest request) {
        Booking booking = bookingRepository.findByIdAndUserId(request.getBookingId(), userId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));

        // PENDING 상태에서만 결제 가능
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new DomainException(DomainExceptionCode.INVALID_BOOKING_STATUS);
        }

        // 중복 결제 방지: 결제 완료 후 Consumer가 예약을 확정하기 전까지는 예약이 PENDING이므로
        // 이미 PAID 결제가 있는지 함께 확인한다.
        if (paymentRepository.existsByBookingIdAndPaymentStatus(booking.getId(), PaymentStatus.PAID)) {
            throw new DomainException(DomainExceptionCode.ALREADY_PAID_BOOKING);
        }

        PgResponse pgResponse = pgClient.pay(request.getPaymentMethod(), booking.getTotPrice());
        if (!pgResponse.isSuccess()) {
            throw new DomainException(DomainExceptionCode.PAYMENT_FAILED);
        }

        Payment payment = Payment.builder()
            .booking(booking)
            .paymentMethod(request.getPaymentMethod())
            .paidAmount(booking.getTotPrice())
            .tid(pgResponse.getTid())
            .build();
        Payment saved = paymentRepository.save(payment);

        // 결제 성공 → 결제 완료 이벤트 발행
        // 예약 확정(PENDING → CONFIRMED)은 PaymentEventConsumer가 이벤트를 받아 처리한다.
        // 실제 Kafka 전송은 결제 트랜잭션 커밋 이후에 PaymentEventProducer가 수행한다.
        eventPublisher.publishEvent(PaymentCompletedEvent.builder()
            .paymentId(saved.getId())
            .bookingId(booking.getId())
            .tid(saved.getTid())
            .paidAmount(saved.getPaidAmount())
            .build());

        return PaymentResponse.from(saved);
    }

    /**
     * 결제 취소 및 환불
     *
     * <p>결제 취소 시 재고 원복과 쿠폰 복구를 하나의 트랜잭션에서 처리하여
     * 부분 실패로 인한 데이터 불일치를 방지한다.
     *
     * @param userId    취소 요청 유저 ID
     * @param paymentId 취소할 결제 ID
     * @return 취소된 결제 결과
     */
    @Transactional
    public PaymentResponse cancel(Long userId, Long paymentId) {
        // 예약 취소 Saga(refundByBookingCancel)와 동시에 같은 결제를 환불하지 않도록 행을 잠근다.
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_PAYMENT));

        Booking booking = payment.getBooking();
        if (!booking.getUserId().equals(userId)) {
            throw new DomainException(DomainExceptionCode.UNAUTHORIZED_ACCESS);
        }

        // 외부 PG 호출 전에 모든 검증을 끝낸다.
        // (PG 환불 후 검증에 실패해 롤백되면 "PG는 환불됐는데 DB는 PAID"인 불일치가 생긴다)
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new DomainException(DomainExceptionCode.INVALID_PAYMENT_STATUS);
        }
        // 예약이 이미 취소된 경우(예약 취소 API로 먼저 취소됨)는 환불만 진행한다.
        boolean bookingAlreadyCancelled = booking.getStatus() == BookingStatus.CANCELLED;
        if (!bookingAlreadyCancelled && booking.getStatus() != BookingStatus.PENDING
            && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new DomainException(DomainExceptionCode.CANNOT_CANCEL);
        }

        // 1. PG 취소
        if (!pgClient.cancel(payment.getTid()).isSuccess()) {
            throw new DomainException(DomainExceptionCode.REFUND_FAILED);
        }
        // 2. 결제 상태 REFUNDED 처리
        payment.refund();

        // 예약 취소 API에서 이미 예약 취소/재고 원복/쿠폰 복구를 했으므로 중복 처리하지 않는다.
        if (bookingAlreadyCancelled) {
            return PaymentResponse.from(payment);
        }

        // 3. 예약 취소
        booking.cancel("결제 취소");

        // 4. 객실 재고 원복
        List<RoomStock> stocks = roomStockRepository.findByRoomProductIdAndDateBetween(
            booking.getRoomProduct().getId(), booking.getArrDate(), booking.getDepDate().minusDays(1));
        stocks.forEach(RoomStock::increase);

        // 5. 쿠폰 사용 취소 (예약 시 쿠폰 적용된 경우)
        if (booking.getUserCouponId() != null) {
            userCouponRepository.findById(booking.getUserCouponId())
                .ifPresent(UserCoupon::restore);
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 예약 취소에 따른 결제 환불 (Saga 보상 트랜잭션, BookingEventConsumer에서 호출)
     *
     * <p>예약 도메인이 발행한 예약 취소 이벤트를 받아 해당 예약의 PAID 결제를 환불한다.
     * 이벤트는 중복 전달될 수 있으므로 멱등하게 처리한다.
     * <ul>
     *   <li>PAID 결제 있음: PG 취소 후 REFUNDED 처리</li>
     *   <li>PAID 결제 없음(결제 전 취소, 이미 환불됨): 아무것도 하지 않음</li>
     * </ul>
     * 예약 취소/재고 원복/쿠폰 복구는 예약 도메인에서 이미 끝났으므로 여기서는 결제만 다룬다.
     *
     * <p>PG 환불이 실패하면 {@link DomainException}을 던져 DLT로 보내고 운영자가 수동 처리한다.
     * (PG 환불은 같은 tid로 재요청해도 중복 환불되지 않는 것이 일반적이지만, Mock PG라 보장할 수 없어 자동 재시도하지 않는다)
     *
     * @param bookingId 취소된 예약 ID
     */
    @Transactional
    public void refundByBookingCancel(Long bookingId) {
        List<Payment> paidPayments =
            paymentRepository.findByBookingIdAndStatusWithLock(bookingId, PaymentStatus.PAID);

        if (paidPayments.isEmpty()) {
            log.info("환불 대상 결제 없음 (결제 전 취소 또는 이미 환불) - bookingId: {}", bookingId);
            return;
        }

        for (Payment payment : paidPayments) {
            if (!pgClient.cancel(payment.getTid()).isSuccess()) {
                throw new DomainException(DomainExceptionCode.REFUND_FAILED);
            }
            payment.refund();
            log.info("예약 취소에 따른 결제 환불 완료 - bookingId: {}, paymentId: {}, amount: {}",
                bookingId, payment.getId(), payment.getRefundAmount());
        }
    }
}
