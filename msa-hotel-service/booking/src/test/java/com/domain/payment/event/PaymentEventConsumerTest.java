package com.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.domain.booking.entity.Booking;
import com.domain.booking.entity.BookingStatus;
import com.domain.booking.repository.BookingRepository;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomType;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

  @InjectMocks
  private PaymentEventConsumer paymentEventConsumer;

  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private Acknowledgment ack;

  // ─────────────────────────────────────────────────────────────
  // 픽스처
  // ─────────────────────────────────────────────────────────────

  private Booking createPendingBooking(Long bookingId) {
    RoomProduct room = RoomProduct.builder()
        .name("디럭스 더블").roomType(RoomType.DELUXE).price(150000)
        .baseCapacity(2).maxCapacity(4)
        .build();
    Booking booking = Booking.builder()
        .userId(10L).roomProduct(room).bookingNumber("BK001")
        .arrDate(LocalDate.of(2026, 7, 1)).depDate(LocalDate.of(2026, 7, 3))
        .adultCount(2).childCount(0).totPrice(300000)
        .build();
    setField(booking, "id", bookingId);
    return booking;
  }

  private PaymentCompletedEvent createEvent(Long bookingId) {
    return PaymentCompletedEvent.builder()
        .paymentId(1L).bookingId(bookingId).tid("TID-ABC123").paidAmount(300000)
        .build();
  }

  // ─────────────────────────────────────────────────────────────
  // 결제 완료 이벤트 처리 테스트
  // ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("결제 완료 이벤트 수신 - 예약 CONFIRMED 변경 후 ack")
  void onPaymentCompleted_success() {
    Booking booking = createPendingBooking(1L);
    given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));

    paymentEventConsumer.onPaymentCompleted(createEvent(1L), ack);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    verify(bookingRepository).save(booking);
    // 처리 성공 시에만 offset 커밋
    verify(ack).acknowledge();
  }

  @Test
  @DisplayName("결제 완료 이벤트 수신 - 예약이 없으면 ack하지 않음")
  void onPaymentCompleted_bookingNotFound() {
    given(bookingRepository.findById(99L)).willReturn(Optional.empty());

    paymentEventConsumer.onPaymentCompleted(createEvent(99L), ack);

    verify(bookingRepository, never()).save(any());
    verify(ack, never()).acknowledge();
  }

  // ─────────────────────────────────────────────────────────────
  // 테스트 유틸
  // ─────────────────────────────────────────────────────────────

  private void setField(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
