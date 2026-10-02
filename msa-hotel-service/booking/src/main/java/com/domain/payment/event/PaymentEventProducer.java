package com.domain.payment.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 발행기
 *
 * <p>결제가 성공하면 {@link PaymentCompletedEvent}를 {@code payment-completed-events} 토픽으로 발행한다.
 * 예약 확정(PENDING → CONFIRMED)은 결제 서비스가 직접 하지 않고,
 * 이 이벤트를 구독하는 {@link PaymentEventConsumer}가 처리한다.
 * 결제 서비스가 예약 도메인을 직접 변경하지 않으므로, 이후 결제 서비스를 별도 MSA로 분리하기 쉬워진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.payment-completed-events}")
    private String paymentCompletedEventsTopic;

    /**
     * 결제 완료 이벤트 발행
     *
     * <p>메시지 key를 bookingId로 지정해 같은 예약의 이벤트가 항상 같은 파티션에 들어가도록 한다.
     * (같은 파티션 안에서는 순서가 보장되므로, 예약 단위로 이벤트 순서가 유지된다)
     */
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(paymentCompletedEventsTopic, String.valueOf(event.getBookingId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("결제 완료 이벤트 발행 실패 - bookingId: {}, paymentId: {}",
                        event.getBookingId(), event.getPaymentId(), ex);
                } else {
                    log.info("결제 완료 이벤트 발행 완료 - bookingId: {}, topic: {}",
                        event.getBookingId(), paymentCompletedEventsTopic);
                }
            });
    }
}
