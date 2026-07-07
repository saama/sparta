package com.domain.payment.dto.request;

import com.domain.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class PaymentCreateRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    private PaymentMethod paymentMethod;
}
