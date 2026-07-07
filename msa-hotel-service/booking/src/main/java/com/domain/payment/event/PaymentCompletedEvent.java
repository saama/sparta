package com.domain.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private Long paymentId;
    private Long bookingId;
    private String tid;
    private Integer paidAmount;
}
