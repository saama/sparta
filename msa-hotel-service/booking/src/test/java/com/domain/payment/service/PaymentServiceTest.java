package com.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.domain.booking.entity.Booking;
import com.domain.booking.entity.BookingStatus;
import com.domain.booking.repository.BookingRepository;
import com.domain.coupon.entity.Coupon;
import com.domain.coupon.entity.DiscountType;
import com.domain.coupon.entity.UserCoupon;
import com.domain.coupon.repository.UserCouponRepository;
import com.domain.payment.client.PgClient;
import com.domain.payment.client.PgResponse;
import com.domain.payment.dto.request.PaymentCreateRequest;
import com.domain.payment.dto.response.PaymentResponse;
import com.domain.payment.entity.Payment;
import com.domain.payment.entity.PaymentMethod;
import com.domain.payment.entity.PaymentStatus;
import com.domain.payment.event.PaymentCompletedEvent;
import com.domain.payment.repository.PaymentRepository;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomStock;
import com.domain.room.entity.RoomType;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @InjectMocks
  private PaymentService paymentService;

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private UserCouponRepository userCouponRepository;
  @Mock
  private RoomStockRepository roomStockRepository;
  @Mock
  private PgClient pgClient;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  // ─────────────────────────────────────────────────────────────
  // 픽스처
  // ─────────────────────────────────────────────────────────────

  private RoomProduct createRoom() {
    RoomProduct room = RoomProduct.builder()
        .name("디럭스 더블")
        .roomType(RoomType.DELUXE)
        .price(150000)
        .baseCapacity(2)
        .maxCapacity(4)
        .build();
    setField(room, "id", 1L);
    setField(room, "isActive", true);
    return room;
  }

  /** PENDING 상태 예약 생성 */
  private Booking createPendingBooking(Long bookingId, Long userId, RoomProduct room,
      Integer totPrice) {
    Booking booking = Booking.builder()
        .userId(userId).roomProduct(room).bookingNumber("BK001")
        .arrDate(LocalDate.of(2026, 7, 1)).depDate(LocalDate.of(2026, 7, 3))
        .adultCount(2).childCount(0).totPrice(totPrice)
        .build();
    setField(booking, "id", bookingId);
    // Booking 빌더에서 status = PENDING 으로 설정되므로 별도 처리 불필요
    return booking;
  }

  /** CONFIRMED 상태 예약 생성 */
  private Booking createConfirmedBooking(Long bookingId, Long userId, RoomProduct room) {
    Booking booking = createPendingBooking(bookingId, userId, room, 300000);
    booking.confirm(); // PENDING → CONFIRMED
    return booking;
  }

  private Payment createPayment(Long paymentId, Booking booking) {
    Payment payment = Payment.builder()
        .booking(booking)
        .paymentMethod(PaymentMethod.CARD)
        .paidAmount(booking.getTotPrice())
        .tid("TID-MOCK12345678")
        .build();
    setField(payment, "id", paymentId);
    return payment;
  }

  // ─────────────────────────────────────────────────────────────
  // 결제 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("결제 성공 - 결제 완료 이벤트 발행, 예약 확정은 Consumer에 위임")
  void pay_success() {
    RoomProduct room = createRoom();
    Booking booking = createPendingBooking(1L, 10L, room, 300000);

    PaymentCreateRequest request = new PaymentCreateRequest();
    setField(request, "bookingId", 1L);
    setField(request, "paymentMethod", PaymentMethod.CARD);

    Payment savedPayment = createPayment(1L, booking);

    given(bookingRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.of(booking));
    given(paymentRepository.existsByBookingIdAndPaymentStatus(1L, PaymentStatus.PAID))
        .willReturn(false);
    given(pgClient.pay(PaymentMethod.CARD, 300000)).willReturn(PgResponse.success("TID-ABC123"));
    given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

    PaymentResponse response = paymentService.pay(10L, request);

    assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    verify(paymentRepository).save(any(Payment.class));

    // 결제 서비스는 예약을 직접 확정하지 않는다 (Consumer가 이벤트로 처리)
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);

    // 발행된 이벤트 내용 검증
    ArgumentCaptor<PaymentCompletedEvent> captor =
        ArgumentCaptor.forClass(PaymentCompletedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    PaymentCompletedEvent event = captor.getValue();
    assertThat(event.getPaymentId()).isEqualTo(1L);
    assertThat(event.getBookingId()).isEqualTo(1L);
    assertThat(event.getTid()).isEqualTo("TID-MOCK12345678");
    assertThat(event.getPaidAmount()).isEqualTo(300000);
  }

  @Test
  @DisplayName("결제 실패 - 이미 결제된 예약 (예약 확정 전 중복 결제 요청)")
  void pay_fail_alreadyPaid() {
    RoomProduct room = createRoom();
    Booking booking = createPendingBooking(1L, 10L, room, 300000); // 아직 Consumer 처리 전이라 PENDING

    PaymentCreateRequest request = new PaymentCreateRequest();
    setField(request, "bookingId", 1L);
    setField(request, "paymentMethod", PaymentMethod.CARD);

    given(bookingRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.of(booking));
    given(paymentRepository.existsByBookingIdAndPaymentStatus(1L, PaymentStatus.PAID))
        .willReturn(true);

    assertThatThrownBy(() -> paymentService.pay(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("이미 결제가 완료된 예약입니다");

    // PG 호출과 이벤트 발행이 일어나지 않아야 한다
    verify(pgClient, never()).pay(any(), anyInt());
    verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
  }

  @Test
  @DisplayName("결제 실패 - PG 결제 실패 시 이벤트 미발행")
  void pay_fail_pgFailed() {
    RoomProduct room = createRoom();
    Booking booking = createPendingBooking(1L, 10L, room, 300000);

    PaymentCreateRequest request = new PaymentCreateRequest();
    setField(request, "bookingId", 1L);
    setField(request, "paymentMethod", PaymentMethod.CARD);

    given(bookingRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.of(booking));
    given(paymentRepository.existsByBookingIdAndPaymentStatus(1L, PaymentStatus.PAID))
        .willReturn(false);
    given(pgClient.pay(PaymentMethod.CARD, 300000)).willReturn(PgResponse.failure("한도 초과"));

    assertThatThrownBy(() -> paymentService.pay(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("결제 처리에 실패하였습니다");

    verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
  }

  @Test
  @DisplayName("결제 실패 - PENDING이 아닌 예약")
  void pay_fail_invalidStatus() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room); // 이미 CONFIRMED

    PaymentCreateRequest request = new PaymentCreateRequest();
    setField(request, "bookingId", 1L);
    setField(request, "paymentMethod", PaymentMethod.CARD);

    given(bookingRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.of(booking));

    assertThatThrownBy(() -> paymentService.pay(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("현재 상태에서는 해당 작업을 수행할 수 없습니다");
  }

  @Test
  @DisplayName("결제 실패 - 존재하지 않는 예약")
  void pay_fail_bookingNotFound() {
    PaymentCreateRequest request = new PaymentCreateRequest();
    setField(request, "bookingId", 99L);
    setField(request, "paymentMethod", PaymentMethod.CARD);

    given(bookingRepository.findByIdAndUserId(99L, 10L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.pay(10L, request))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("예약 정보를 찾을 수 없습니다");
  }

  // ─────────────────────────────────────────────────────────────
  // 결제 취소 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("결제 취소 성공 - 재고 원복")
  void cancel_success_restoresStock() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    Payment payment = createPayment(1L, booking);

    RoomStock stock = RoomStock.builder()
        .roomProduct(room).date(LocalDate.of(2026, 7, 1)).stock(2).build();

    given(paymentRepository.findByIdWithLock(1L)).willReturn(Optional.of(payment));
    given(pgClient.cancel("TID-MOCK12345678")).willReturn(PgResponse.success("TID-MOCK12345678"));
    given(roomStockRepository.findByRoomProductIdAndDateBetween(any(), any(), any()))
        .willReturn(List.of(stock));

    PaymentResponse response = paymentService.cancel(10L, 1L);

    // 결제 REFUNDED 검증
    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    // 예약 CANCELLED 검증
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    // 재고 복구 검증 (2 → 3)
    assertThat(stock.getStock()).isEqualTo(3);
  }

  @Test
  @DisplayName("결제 취소 성공 - 쿠폰 복구")
  void cancel_success_restoresCoupon() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    setField(booking, "userCouponId", 50L); // 쿠폰 사용 이력
    Payment payment = createPayment(1L, booking);

    Coupon coupon = Coupon.builder()
        .code("TEST").name("테스트").discountType(DiscountType.FIXED)
        .discountValue(10000).minPrice(0)
        .validFrom(LocalDate.now()).validUntil(LocalDate.now().plusDays(10))
        .build();
    UserCoupon userCoupon = UserCoupon.builder().userId(10L).coupon(coupon).build();
    userCoupon.use(); // 사용 상태

    given(paymentRepository.findByIdWithLock(1L)).willReturn(Optional.of(payment));
    given(pgClient.cancel(any())).willReturn(PgResponse.success("TID"));
    given(roomStockRepository.findByRoomProductIdAndDateBetween(any(), any(), any()))
        .willReturn(List.of());
    given(userCouponRepository.findById(50L)).willReturn(Optional.of(userCoupon));

    paymentService.cancel(10L, 1L);

    // 쿠폰 복구 검증
    assertThat(userCoupon.getIsUsed()).isFalse();
  }

  @Test
  @DisplayName("결제 취소 실패 - 타인의 결제")
  void cancel_fail_notOwner() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room); // userId = 10L
    Payment payment = createPayment(1L, booking);

    given(paymentRepository.findByIdWithLock(1L)).willReturn(Optional.of(payment));

    // userId 99L 로 취소 시도 → 권한 없음
    assertThatThrownBy(() -> paymentService.cancel(99L, 1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("인증되지 않은 접근");
  }

  @Test
  @DisplayName("결제 취소 실패 - 존재하지 않는 결제")
  void cancel_fail_paymentNotFound() {
    given(paymentRepository.findByIdWithLock(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.cancel(10L, 99L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("결제 정보를 찾을 수 없습니다");
  }

  @Test
  @DisplayName("결제 취소 실패 - 이미 환불된 결제는 PG 호출 없이 거부 (이중 환불 방지)")
  void cancel_fail_alreadyRefunded() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    Payment payment = createPayment(1L, booking);
    payment.refund(); // 예약 취소 Saga로 이미 환불됨

    given(paymentRepository.findByIdWithLock(1L)).willReturn(Optional.of(payment));

    assertThatThrownBy(() -> paymentService.cancel(10L, 1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("환불할 수 없는 결제 상태입니다");
    verify(pgClient, never()).cancel(any());
  }

  @Test
  @DisplayName("결제 취소 - 예약이 이미 취소된 경우 환불만 하고 재고/쿠폰은 중복 복구하지 않음")
  void cancel_bookingAlreadyCancelled_refundOnly() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    booking.cancel("예약 취소"); // 예약 취소 API로 먼저 취소됨 (재고/쿠폰은 그때 복구됨)
    Payment payment = createPayment(1L, booking);

    given(paymentRepository.findByIdWithLock(1L)).willReturn(Optional.of(payment));
    given(pgClient.cancel("TID-MOCK12345678")).willReturn(PgResponse.success("TID-MOCK12345678"));

    paymentService.cancel(10L, 1L);

    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    verify(roomStockRepository, never()).findByRoomProductIdAndDateBetween(any(), any(), any());
  }

  @Test
  @DisplayName("결제 취소 실패 - PG 환불 실패 시 REFUNDED로 바꾸지 않음")
  void cancel_fail_pgRefundFailed() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    Payment payment = createPayment(1L, booking);

    given(paymentRepository.findByIdWithLock(1L)).willReturn(Optional.of(payment));
    given(pgClient.cancel(any())).willReturn(PgResponse.failure("PG 오류"));

    assertThatThrownBy(() -> paymentService.cancel(10L, 1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("환불 처리에 실패하였습니다");
    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
  }

  // ─────────────────────────────────────────────────────────────
  // 예약 취소 Saga - 결제 환불 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("예약 취소 환불 - PAID 결제를 PG 취소 후 REFUNDED 처리")
  void refundByBookingCancel_success() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    Payment payment = createPayment(1L, booking);

    given(paymentRepository.findByBookingIdAndStatusWithLock(1L, PaymentStatus.PAID))
        .willReturn(List.of(payment));
    given(pgClient.cancel("TID-MOCK12345678")).willReturn(PgResponse.success("TID-MOCK12345678"));

    paymentService.refundByBookingCancel(1L);

    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(payment.getRefundAmount()).isEqualTo(300000);
  }

  @Test
  @DisplayName("예약 취소 환불 - PAID 결제가 없으면(결제 전 취소/이미 환불) PG 호출 없이 종료")
  void refundByBookingCancel_noPaidPayment() {
    given(paymentRepository.findByBookingIdAndStatusWithLock(1L, PaymentStatus.PAID))
        .willReturn(List.of());

    paymentService.refundByBookingCancel(1L);

    verify(pgClient, never()).cancel(any());
  }

  @Test
  @DisplayName("예약 취소 환불 실패 - PG 환불 실패 시 예외 (DLT로 이동해 수동 처리)")
  void refundByBookingCancel_pgFailed() {
    RoomProduct room = createRoom();
    Booking booking = createConfirmedBooking(1L, 10L, room);
    Payment payment = createPayment(1L, booking);

    given(paymentRepository.findByBookingIdAndStatusWithLock(1L, PaymentStatus.PAID))
        .willReturn(List.of(payment));
    given(pgClient.cancel(any())).willReturn(PgResponse.failure("PG 오류"));

    assertThatThrownBy(() -> paymentService.refundByBookingCancel(1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("환불 처리에 실패하였습니다");
    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
  }

  // ─────────────────────────────────────────────────────────────
  // 테스트 유틸
  // ─────────────────────────────────────────────────────────────

  private void setField(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = obj.getClass().getSuperclass();
      try {
        var field = superClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
