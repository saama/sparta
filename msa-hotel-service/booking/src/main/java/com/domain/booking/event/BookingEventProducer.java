package com.domain.booking.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.booking-events}")
    private String bookingEventsTopic;

    public void publishBookingCreated(BookingCreatedEvent event) {
        kafkaTemplate.send(bookingEventsTopic, String.valueOf(event.getBookingId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("예약 이벤트 발행 실패 - bookingId: {}", event.getBookingId(), ex);
                } else {
                    log.info("예약 이벤트 발행 완료 - bookingId: {}, topic: {}",
                        event.getBookingId(), bookingEventsTopic);
                }
            });
    }
}
