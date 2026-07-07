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
import com.domain.payment.repository.PaymentRepository;
import com.domain.room.entity.RoomStock;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 서비스
 *
 * <p>가상 PG({@link PgClient})를 통해 결제를 처리하며, 결제 성공 시 예약 상태를 CONFIRMED로 전환한다.
 *
 * <p>결제 취소 흐름:
 * <ol>
 *   <li>PG 취소 요청</li>
 *   <li>Payment 상태 REFUNDED 처리</li>
 *   <li>Booking 상태 CANCELLED 처리</li>
 *   <li>객실 재고 원복</li>
 *   <li>사용된 쿠폰 복구 (있는 경우)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserCouponRepository userCouponRepository;
    private final RoomStockRepository roomStockRepository;
    private final PgClient pgClient;

    /**
     * 결제 처리
     *
     * <p>PENDING 상태 예약에 대해서만 결제를 허용한다.
     * PG 결제 성공 시 예약 상태를 CONFIRMED로 변경한다.
     *
     * @param userId  결제 요청 유저 ID
     * @param request 결제 수단 및 예약 ID
     * @return 결제 결과
     */
    @Transactional
    public PaymentResponse pay(Long userId, PaymentCreateRequest request) {
        Booking booking = bookingRepository.findByIdAndUserId(request.getBookingId(), userId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));

        // PENDING 상태에서만 결제 가능 (중복 결제 방지)
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new DomainException(DomainExceptionCode.INVALID_BOOKING_STATUS);
        }

        PgResponse pgResponse = pgClient.pay(request.getPaymentMethod(), booking.getTotPrice());
        if (!pgResponse.isSuccess()) {
            throw new DomainException(DomainExceptionCode.PAYMENT_FAILED);
        }

        // 결제 성공 → 예약 확정
        booking.confirm();

        Payment payment = Payment.builder()
            .booking(booking)
            .paymentMethod(request.getPaymentMethod())
            .paidAmount(booking.getTotPrice())
            .tid(pgResponse.getTid())
            .build();

        return PaymentResponse.from(paymentRepository.save(payment));
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
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_PAYMENT));

        Booking booking = payment.getBooking();
        if (!booking.getUserId().equals(userId)) {
            throw new DomainException(DomainExceptionCode.UNAUTHORIZED_ACCESS);
        }

        // 1. PG 취소
        pgClient.cancel(payment.getTid());
        // 2. 결제 상태 REFUNDED 처리
        payment.refund();

        // 3. 예약 취소
        try {
            booking.cancel("결제 취소");
        } catch (IllegalStateException e) {
            throw new DomainException(DomainExceptionCode.CANNOT_CANCEL);
        }

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
}
