package com.domain.payment.event;

import com.domain.booking.event.BookingCancelledEvent;
import com.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 예약 이벤트 구독기 (결제 도메인)
 *
 * <p>Saga 보상 트랜잭션: 예약이 취소되면 {@link BookingCancelledEvent}를 받아 해당 예약의 결제를 환불한다.
 *
 * <pre>
 *  예약 서비스                      Kafka                         결제 서비스
 *  예약 취소(커밋) ──> booking-cancelled-events ──> BookingEventConsumer ──> 결제 환불
 * </pre>
 * 중앙 오케스트레이터 없이 각 도메인이 이벤트에 반응하는 코레오그래피(Choreography) 방식이다.
 *
 * <p>{@link PaymentEventConsumer}와 같은 원칙을 따른다: 서비스 트랜잭션이 커밋된 뒤 ack하고,
 * 예외는 삼키지 않고 던져 {@code KafkaErrorHandlerConfig}의 재시도/DLT 정책에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "${app.kafka.topics.booking-cancelled-events}",
        groupId = "${spring.kafka.consumer.group-id}")
    public void onBookingCancelled(BookingCancelledEvent event, Acknowledgment ack) {
        // 환불 대상이 없거나 이미 환불된 경우도 예외 없이 끝나도록 서비스에서 멱등하게 처리한다.
        paymentService.refundByBookingCancel(event.getBookingId());
        log.info("예약 취소 이벤트 처리 - bookingId: {}, reason: {}",
            event.getBookingId(), event.getCancelReason());
        ack.acknowledge();
    }
}
