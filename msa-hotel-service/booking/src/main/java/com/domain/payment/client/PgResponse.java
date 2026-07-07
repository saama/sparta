package com.domain.payment.client;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PgResponse {

    private boolean success;
    private String tid;
    private String message;

    public static PgResponse success(String tid) {
        PgResponse res = new PgResponse();
        res.success = true;
        res.tid = tid;
        return res;
    }

    public static PgResponse failure(String message) {
        PgResponse res = new PgResponse();
        res.success = false;
        res.message = message;
        return res;
    }
}
