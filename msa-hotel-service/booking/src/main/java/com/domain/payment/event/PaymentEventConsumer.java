package com.domain.payment.event;

import com.domain.booking.entity.Booking;
import com.domain.booking.repository.BookingRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final BookingRepository bookingRepository;

    @KafkaListener(topics = "payment-completed-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        try {
            Booking booking = bookingRepository.findById(event.getBookingId())
                .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));
            booking.confirm();
            bookingRepository.save(booking);
            log.info("결제 완료 이벤트 처리 - bookingId: {}, tid: {}", event.getBookingId(), event.getTid());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패 - bookingId: {}", event.getBookingId(), e);
        }
    }
}
