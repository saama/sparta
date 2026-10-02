package com.domain.booking.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약 이벤트 발행기
 *
 * <p>서비스는 {@code ApplicationEventPublisher}로 Spring 내부 이벤트만 발행하고,
 * 이 클래스가 트랜잭션 커밋이 성공한 뒤(AFTER_COMMIT)에 Kafka로 전송한다.
 * 트랜잭션 안에서 바로 send()하면, 이후 롤백될 경우 DB에 존재하지 않는 예약의 이벤트가
 * 이미 외부로 나가 버리는 문제가 생긴다. 롤백되면 이 리스너는 호출되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.booking-events}")
    private String bookingEventsTopic;

    @Value("${app.kafka.topics.booking-cancelled-events}")
    private String bookingCancelledEventsTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishBookingCreated(BookingCreatedEvent event) {
        kafkaTemplate.send(bookingEventsTopic, String.valueOf(event.getBookingId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    // 커밋 이후라 DB는 이미 반영된 상태 → 발행 실패는 로그로 남긴다.
                    // (DB 커밋과 발행을 완전히 원자적으로 묶으려면 Transactional Outbox 패턴이 필요)
                    log.error("예약 이벤트 발행 실패 - bookingId: {}", event.getBookingId(), ex);
                } else {
                    log.info("예약 이벤트 발행 완료 - bookingId: {}, topic: {}",
                        event.getBookingId(), bookingEventsTopic);
                }
            });
    }

    /**
     * 예약 취소 이벤트 발행 (Saga 보상 트리거)
     *
     * <p>예약 취소가 커밋된 뒤에만 발행해야 한다. 취소가 롤백됐는데 이벤트가 나가면
     * 유효한 예약의 결제가 환불되어 버린다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishBookingCancelled(BookingCancelledEvent event) {
        kafkaTemplate.send(bookingCancelledEventsTopic, String.valueOf(event.getBookingId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("예약 취소 이벤트 발행 실패 - bookingId: {}", event.getBookingId(), ex);
                } else {
                    log.info("예약 취소 이벤트 발행 완료 - bookingId: {}, topic: {}",
                        event.getBookingId(), bookingCancelledEventsTopic);
                }
            });
    }
}
