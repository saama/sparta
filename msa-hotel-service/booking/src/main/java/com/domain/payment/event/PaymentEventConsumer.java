package com.domain.payment.event;

import com.domain.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 결제 완료 이벤트 구독기
 *
 * <p>{@link PaymentEventProducer}가 발행한 {@link PaymentCompletedEvent}를 받아
 * 예약 상태를 PENDING → CONFIRMED로 변경한다.
 *
 * <p>Consumer는 메시지 수신과 ack만 담당하고, 상태 변경은 트랜잭션이 걸린
 * {@link BookingService#confirmByPayment}에 위임한다. 서비스 호출이 반환된 시점에는
 * 이미 DB 커밋이 끝났으므로, 그 뒤에 ack해야 "ack는 됐는데 DB는 롤백"되는 유실을 막을 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final BookingService bookingService;

    // 토픽명은 Producer와 같은 설정값을 사용해 하드코딩으로 인한 불일치를 방지한다.
    @KafkaListener(topics = "${app.kafka.topics.payment-completed-events}",
        groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        try {
            // 중복/지연 이벤트도 예외 없이 처리되도록 서비스에서 멱등하게 구현되어 있다.
            bookingService.confirmByPayment(event.getBookingId(), event.getPaymentId());
            log.info("결제 완료 이벤트 처리 - bookingId: {}, tid: {}", event.getBookingId(), event.getTid());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패 - bookingId: {}", event.getBookingId(), e);
        }
    }
}
