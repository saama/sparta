package com.domain.payment.controller;

import com.domain.payment.dto.request.PaymentCreateRequest;
import com.domain.payment.dto.response.PaymentResponse;
import com.domain.payment.service.PaymentService;
import com.global.response.ApiResponse;
import com.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
        @Valid @RequestBody PaymentCreateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(paymentService.pay(userDetails.getUserId(), request)));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
        @PathVariable Long paymentId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(paymentService.cancel(userDetails.getUserId(), paymentId)));
    }
}
