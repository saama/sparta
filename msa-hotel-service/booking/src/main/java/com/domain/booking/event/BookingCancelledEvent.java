package com.domain.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약 취소 이벤트
 *
 * <p>Saga(코레오그래피 방식)의 보상 트리거. 예약이 취소되면 발행되고,
 * 결제 도메인({@code BookingEventConsumer})이 구독해 해당 예약의 결제를 환불한다.
 * 예약 서비스는 결제 도메인을 직접 호출하지 않고 "취소되었다"는 사실만 알린다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {

    private Long bookingId;
    private Long userId;
    private String cancelReason;
}
