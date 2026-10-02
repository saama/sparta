package com.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.domain.booking.event.BookingCancelledEvent;
import com.domain.payment.service.PaymentService;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Consumer는 메시징(서비스 위임 + ack)만 검증한다.
 * 환불 로직은 PaymentServiceTest의 refundByBookingCancel 테스트에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

  @InjectMocks
  private BookingEventConsumer bookingEventConsumer;

  @Mock
  private PaymentService paymentService;
  @Mock
  private Acknowledgment ack;

  private BookingCancelledEvent createEvent(Long bookingId) {
    return BookingCancelledEvent.builder()
        .bookingId(bookingId).userId(10L).cancelReason("일정 변경")
        .build();
  }

  @Test
  @DisplayName("예약 취소 이벤트 수신 - 환불 처리 후 ack")
  void onBookingCancelled_success() {
    bookingEventConsumer.onBookingCancelled(createEvent(1L), ack);

    verify(paymentService).refundByBookingCancel(1L);
    verify(ack).acknowledge();
  }

  @Test
  @DisplayName("예약 취소 이벤트 수신 - 환불 실패 시 예외를 에러 핸들러로 전파하고 ack하지 않음")
  void onBookingCancelled_fail() {
    willThrow(new DomainException(DomainExceptionCode.REFUND_FAILED))
        .given(paymentService).refundByBookingCancel(1L);

    assertThatThrownBy(() -> bookingEventConsumer.onBookingCancelled(createEvent(1L), ack))
        .isInstanceOf(DomainException.class);

    verify(ack, never()).acknowledge();
  }
}
