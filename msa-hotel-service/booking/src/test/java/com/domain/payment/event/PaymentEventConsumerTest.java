package com.domain.payment.event;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.domain.booking.service.BookingService;
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
 * 상태별 멱등 처리 로직은 BookingServiceTest의 confirmByPayment 테스트에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

  @InjectMocks
  private PaymentEventConsumer paymentEventConsumer;

  @Mock
  private BookingService bookingService;
  @Mock
  private Acknowledgment ack;

  private PaymentCompletedEvent createEvent(Long bookingId) {
    return PaymentCompletedEvent.builder()
        .paymentId(1L).bookingId(bookingId).tid("TID-ABC123").paidAmount(300000)
        .build();
  }

  @Test
  @DisplayName("결제 완료 이벤트 수신 - 서비스 처리 후 ack")
  void onPaymentCompleted_success() {
    paymentEventConsumer.onPaymentCompleted(createEvent(1L), ack);

    verify(bookingService).confirmByPayment(1L, 1L);
    // 처리(커밋) 성공 시에만 offset 커밋
    verify(ack).acknowledge();
  }

  @Test
  @DisplayName("결제 완료 이벤트 수신 - 서비스 처리 실패 시 ack하지 않음")
  void onPaymentCompleted_fail() {
    willThrow(new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING))
        .given(bookingService).confirmByPayment(99L, 1L);

    paymentEventConsumer.onPaymentCompleted(createEvent(99L), ack);

    verify(ack, never()).acknowledge();
  }
}
