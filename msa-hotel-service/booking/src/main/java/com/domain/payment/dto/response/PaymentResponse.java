package com.domain.payment.dto.response;

import com.domain.payment.entity.Payment;
import com.domain.payment.entity.PaymentMethod;
import com.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponse {

    private Long id;
    private Long bookingId;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private Integer paidAmount;
    private Integer refundAmount;
    private String tid;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
            .id(payment.getId())
            .bookingId(payment.getBooking().getId())
            .paymentMethod(payment.getPaymentMethod())
            .paymentStatus(payment.getPaymentStatus())
            .paidAmount(payment.getPaidAmount())
            .refundAmount(payment.getRefundAmount())
            .tid(payment.getTid())
            .paidAt(payment.getPaidAt())
            .refundedAt(payment.getRefundedAt())
            .build();
    }
}
