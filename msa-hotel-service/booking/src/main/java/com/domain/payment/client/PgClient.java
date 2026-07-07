package com.domain.payment.client;

import com.domain.payment.entity.PaymentMethod;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PgClient {

    public PgResponse pay(PaymentMethod method, int amount) {
        String tid = "TID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return PgResponse.success(tid);
    }

    public PgResponse cancel(String tid) {
        return PgResponse.success(tid);
    }
}
