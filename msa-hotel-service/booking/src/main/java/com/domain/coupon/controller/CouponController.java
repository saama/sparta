package com.domain.coupon.controller;

import com.domain.coupon.dto.request.CouponCreateRequest;
import com.domain.coupon.dto.response.CouponResponse;
import com.domain.coupon.dto.response.UserCouponResponse;
import com.domain.coupon.service.CouponService;
import com.global.response.ApiResponse;
import com.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/api/admin/coupons")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
        @Valid @RequestBody CouponCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.createCoupon(request)));
    }

    @PostMapping("/api/coupons/{couponId}/issue")
    public ResponseEntity<ApiResponse<UserCouponResponse>> issueCoupon(
        @PathVariable Long couponId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(couponService.issueCoupon(userDetails.getUserId(), couponId)));
    }

    @GetMapping("/api/coupons/me")
    public ResponseEntity<ApiResponse<List<UserCouponResponse>>> getMyCoupons(
        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.ok(couponService.getMyCoupons(userDetails.getUserId())));
    }
}
